import model.Course;
import model.User;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import service.CourseService;
import service.UserService;

import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 */
public class Bot extends TelegramLongPollingBot {

    public static final String SPREAD_SHEET = "https://docs.google.com/spreadsheets/d/15xpB2ckMNQLmgwTmJv0DsRjrvsjAYPAC0Im3QFWdqZM/edit#gid=516269660";
    private Parser parser = new Parser();
    private static UserService userService = new UserService();
    private CourseService courseService = new CourseService();
    private static final long ADMIN_ID = 364224503;
    private static final int REMINDER_INTERVAL = 300000;

    void setParser(Parser parser) {
        this.parser = parser;
    }

    static void setUserService(UserService userService) {
        Bot.userService = userService;
    }

    //https://api.telegram.org/bot930074549:AAEzjxP7aFUkpBs4dgn8QbUgpb5JeDRd3vo/getUpdates
    /**
    Covered use cases:
    [Use case #4.1] The bot sends notifications when Admin makes changes in the elective course schedule on the Google Sheet
    [Use case #4.2] The bot sends notifications one hour before commencement of elective course
     */
    public static void main(String[] args) {


        int reminders_count = 0;
        ApiContextInitializer.init();
        try {
            Parser.initTableLoad();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            Bot myBot = new Bot();
            telegramBotsApi.registerBot(new Bot());
            while (true) {
                try {
                    Thread.sleep(REMINDER_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    HashMap<Integer, String> currentCourses = Parser.getCoursesByDatetime();
                    for (User user : userService.findAllUsers()) {
                        for (int courseId : currentCourses.keySet()) {
                            if (userService.getCourseById(user, courseId)!=null) {
                                myBot.sendMsg(user.getChat_id(), "\u2757 Reminder: course " + currentCourses.get(courseId) + " in the next hour!");
                            }
                        }
                    }
                //Start checking changes if 6 reminders passed by, because 6*300000 = 30 min
                reminders_count++;
                    if (reminders_count == 6) {
                    reminders_count = 0;
                    List<Integer> changesIds = Parser.getChangesCoursesIds();
                    for (User user : userService.findAllUsers()) {
                        System.out.println("user id" + user.getId());
                        for (int courseId : changesIds) {
                            System.out.println("Course ID:" + courseId);
                            Course course = userService.getCourseById(user, courseId);
                            if (course!=null) {
                                myBot.sendMsg(user.getChat_id(), "\u2757 Changes in your course: " + course.getName());
                            }
                        }
                    }
                }
                } catch (GeneralSecurityException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
    }
    /**
        Following Use cases are covered :
        [Use case #1] User starts the bot;
        [Use case #2] View entire elective course schedule
        [Use case #3] User  wants to choose an elective courses in order to get notifications
        [Use case #5] User  wants to unsubscribe from the bot
    */
    BotApiMethod<? extends Serializable> getMethod(Update update) {
        // We check if the update has a message and the message has text
        if (update.hasMessage() && update.getMessage().hasText()) {

            System.out.println("First name:" + update.getMessage().getFrom().getFirstName());
            System.out.println("Last name:" + update.getMessage().getFrom().getLastName());
            System.out.println("User id:" + update.getMessage().getFrom().getId());
            System.out.println("Chat_id:" + update.getMessage().getChatId());
            System.out.println();

            String message_text = update.getMessage().getText();
            long chat_id = update.getMessage().getChatId();
            long user_id = update.getMessage().getFrom().getId();

            switch (message_text) {
                case "/start":
                    User user = userService.findUser(user_id);
                    if (user == null) {
                        userService.saveUser(new User(user_id, chat_id));
                        System.out.println("new user registered");
                    }
                    System.out.println("user registered already");
                    return sendMenu(chat_id);
                case "All courses":
                    sendMsg(chat_id, SPREAD_SHEET);
                    break;
                case "Choose course":
                    return sendCourses(chat_id);
                case "Finish choosing process":
                    sendMenu(chat_id);
                    break;
                case "Unsubscribe":
                    YNDialogue(chat_id);
                    break;
                case "Yes":
                    unsubscribeUserFromBot(chat_id);
                    break;
                case "No":
                    sendMenu(chat_id);
                    break;
                case "Upload Courses":
                    if (user_id == ADMIN_ID) {
                        saveCoursesInDataBase();
                        sendMsg(chat_id, "courses succesfully updated");
                    }
                    else {
                        sendMsg(chat_id, "Sorry, I don't understand you :(");
                    }
                    break;
                default:
                    sendMsg(chat_id, "Sorry, I don't understand you :(");
            }


        } else if (update.hasCallbackQuery()) {
            long course_id = Long.parseLong(update.getCallbackQuery().getData());
            User user = userService.findUser(update.getCallbackQuery().getFrom().getId());
            Course course = userService.getCourseById(user, course_id);

            if (course == null) {
                userService.subscribeUser(user, new Course(courseService.findCourse(course_id).getName(), course_id));
            } else {
                userService.unSubscribeUser(user, course);
            }

            long message_id = update.getCallbackQuery().getMessage().getMessageId();
            long chat_id = update.getCallbackQuery().getMessage().getChatId();

            InlineKeyboardMarkup markupKeyboard = SetCoursesButtonsForUser(user);

            EditMessageReplyMarkup murkup_message = new EditMessageReplyMarkup()
                    .setChatId(chat_id)
                    .setMessageId(Math.toIntExact(message_id))
                    .setReplyMarkup(markupKeyboard);
            return murkup_message;
        }
        return null;
    }

    public void onUpdateReceived(Update update) {
        try {
            execute(getMethod(update));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void saveCoursesInDataBase() {
        if (courseService.findAllCourses().size() == 0) {
            List<String> courses = parser.getCourses();
            for (int i = 0; i < courses.size(); i++) {
                String cours = courses.get(i);
                courseService.saveCourse(new Course(cours, i));
            }
        }
    }

    public String getBotUsername() {
        return "InnoElectiveBot";
    }

    public String getBotToken() {
        return "956186353:AAH0uqWY1f9Qt9Csk-x8nFo0iJdIDOb4CJ0";
    }

    public synchronized void sendMsg(long chatId, String s) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setText(s);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }

    public synchronized SendMessage sendMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setText("Hello! \u270b\nYou can view schedule or choose elective courses");
        setButtons(sendMessage, chatId == ADMIN_ID);
        return sendMessage;
    }

    public synchronized SendMessage sendCourses(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setText("Click to any course below to subscribe for it:");
        setInline(sendMessage);
        return sendMessage;
    }

    public synchronized void setButtons(SendMessage sendMessage, boolean isAdmin) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        keyboardFirstRow.add(new KeyboardButton("Choose course"));
        keyboardFirstRow.add(new KeyboardButton("All courses"));
        keyboardFirstRow.add(new KeyboardButton("Unsubscribe"));
        if(isAdmin)
        {
            keyboardFirstRow.add(new KeyboardButton("Upload Courses"));
        }
        keyboard.add(keyboardFirstRow);
        replyKeyboardMarkup.setKeyboard(keyboard);
    }

    public synchronized void setYesNoButtons(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        keyboardFirstRow.add(new KeyboardButton("Yes"));
        keyboardFirstRow.add(new KeyboardButton("No"));
        keyboard.add(keyboardFirstRow);
        replyKeyboardMarkup.setKeyboard(keyboard);
    }

    private void setInline(SendMessage sendMessage) {
        User user = userService.findUser(Long.parseLong(sendMessage.getChatId()));
        InlineKeyboardMarkup markupKeyboard = SetCoursesButtonsForUser(user);
        sendMessage.setReplyMarkup(markupKeyboard);
    }

    public synchronized void answerCallbackQuery(String callbackId, String message) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        answer.setText(message);
        answer.setShowAlert(true);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup SetCoursesButtonsForUser(User user)
    {
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        List<String> courses = parser.getCourses();
        for (int i = 0; i < courses.size(); i++) {
            String course = courses.get(i);
            List<InlineKeyboardButton> button = new ArrayList<>();
            if ((userService.getCourseById(user, i)!=null)) {
                button.add(new InlineKeyboardButton().setText("\u2705 " + course).setCallbackData(String.valueOf(i)));
            } else {
                button.add(new InlineKeyboardButton().setText(course).setCallbackData(String.valueOf(i)));
            }
            buttons.add(button);
        }
        InlineKeyboardMarkup markupKeyboard = new InlineKeyboardMarkup();
        markupKeyboard.setKeyboard(buttons);
        return markupKeyboard;
    }

    /*
    Use case #5: Unsubscribe from bot
     */
    private void unsubscribeUserFromBot(long chatId)
    {
        User user = userService.findUser(chatId);
        if(user != null)
        userService.deleteUser(user);
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(false);
        sendMessage.setChatId(chatId);
        sendMessage.setText("You successfully unsubscribed");
        ReplyKeyboardRemove markup = new ReplyKeyboardRemove();
        sendMessage.setReplyMarkup(markup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }

    private void YNDialogue(long chatId)
    {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setText("Do you want to unsubscribe from bot?");
        setYesNoButtons(sendMessage);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }
}
