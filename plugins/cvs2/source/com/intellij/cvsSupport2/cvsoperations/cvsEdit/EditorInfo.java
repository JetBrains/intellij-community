package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * author: lesya
 */
public class EditorInfo {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo");

  @NonNls private static final String FORMAT = "EEE MMM dd HH:mm:ss yyyy zzz";
  public final static SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(FORMAT, Locale.US));
  @NonNls private static final String FORMAT1 = "dd MMM yyyy HH:mm:ss zzz";
  public final static SyncDateFormat DATE_FORMAT1 = new SyncDateFormat(new SimpleDateFormat(FORMAT1, Locale.US));

  private final String myFilePath;
  private final String myUserName;
  private final Date myEditDate;
  private final String myHostHame;
  private final String myPath;

  public static EditorInfo createOn(String string) {
    String[] strings = string.split("\t");
    if (strings.length != 5) return null;
    return new EditorInfo(strings[0]
                          , strings[1],
                            parse(strings[2]),
                            strings[3],
                            strings[4]);
  }

  private EditorInfo(String filePath, String userName, Date editDate, String hostHame, String path) {
    myFilePath = filePath;
    myUserName = userName;
    myEditDate = editDate;
    myHostHame = hostHame;
    myPath = path;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EditorInfo)) return false;

    final EditorInfo editorInfo = (EditorInfo)o;

    if (!myEditDate.equals(editorInfo.myEditDate)) return false;
    if (!myHostHame.equals(editorInfo.myHostHame)) return false;
    if (!myPath.equals(editorInfo.myPath)) return false;
    if (!myUserName.equals(editorInfo.myUserName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myUserName.hashCode();
    result = 29 * result + myEditDate.hashCode();
    result = 29 * result + myHostHame.hashCode();
    result = 29 * result + myPath.hashCode();
    return result;
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append(myUserName);
    result.append('\t');
    result.append(DATE_FORMAT.format(myEditDate));
    result.append('\t');
    result.append(myHostHame);
    result.append('\t');
    result.append(myPath);
    result.append('\t');

    return result.toString();
  }

  public String getUserName() { return myUserName; }

  public String getHostHame() { return myHostHame; }

  public String getPath() { return myPath; }

  public Date getEditDate() { return myEditDate; }

  public String getFilePath() { return myFilePath; }

  public static Date parse(String s) {
    try {
      return DATE_FORMAT.parse(s);
    }
    catch (ParseException e) {
      try {
        return DATE_FORMAT1.parse(s);
      }
      catch (ParseException e1) {
        LOG.error(e1);
        return new Date();
      }
    }
  }
}
