package com.intellij.cvsSupport2.cvsoperations.cvsAnnotate;

import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * author: lesya
 */
public class Annotation {
  private final String myRevision;
  private final String myUser;
  private final Date myDate;

  public static final SyncDateFormat PRESENTABELE_DATE_FORMAT = new SyncDateFormat(SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT,
                                                                                                                    Locale.getDefault()));

  @NonNls private static final String DATE_FORMAT_STRING = "dd-MMM-yy";
  private final static SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(DATE_FORMAT_STRING, Locale.US));
  public static final String CONTENT_SEPARATOR = ": ";

  public static Annotation createOnMessage(String message) throws ParseException {
    int firstWhiteSpace = message.indexOf(" ");
    String revision = message.substring(0, firstWhiteSpace);
    String tail = message.substring(firstWhiteSpace + 1);
    int endOfDatePosition = tail.indexOf(CONTENT_SEPARATOR);
    String date = tail.substring(endOfDatePosition - DATE_FORMAT_STRING.length() - 1, endOfDatePosition - 1);
    String userWithLeftParantheses = tail.substring(0, endOfDatePosition - DATE_FORMAT_STRING.length() - 1 - 1).trim();
    String user = userWithLeftParantheses.substring(1);
    return new Annotation(revision, user, DATE_FORMAT.parse(date));
  }

  public Annotation(String revision, String user, Date date) {
    myRevision = revision;
    myUser = user;
    myDate = date;
  }

  public String getUserName() {
    return myUser;
  }

  public String getRevision() { return myRevision; }

  public Date getDate() { return myDate; }

  public String getPresentableDateString() {
    return PRESENTABELE_DATE_FORMAT.format(getDate());
  }

  public static String createMessageOn(String message) {
    int index = message.indexOf(CONTENT_SEPARATOR);
    if (index < 0) return "";
    return message.substring(index + CONTENT_SEPARATOR.length(), message.length()).replaceAll("\r", "");
  }
}
