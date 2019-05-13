// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.SyncDateFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * author: lesya
 */
public class EditorInfo {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo");

  public final static SyncDateFormat DATE_FORMAT;
  public final static SyncDateFormat DATE_FORMAT1;
  static {
    SimpleDateFormat delegate = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy zzz", Locale.US);
    delegate.setTimeZone(TimeZone.getTimeZone("GMT"));
    DATE_FORMAT = new SyncDateFormat(delegate);
    SimpleDateFormat delegate1 = new SimpleDateFormat("dd MMM yyyy HH:mm:ss zzz", Locale.US);
    delegate1.setTimeZone(TimeZone.getTimeZone("GMT"));
    DATE_FORMAT1 = new SyncDateFormat(delegate1);
  }

  private final String myFilePath;
  private final String myUserName;
  private final Date myEditDate;
  private final String myHostName;
  private final String myPath;

  public static EditorInfo createOn(String string) {
    final String[] strings = string.split("\t");
    if (strings.length != 5) return null;
    return new EditorInfo(strings[0], strings[1], parse(strings[2]), strings[3], strings[4]);
  }

  private EditorInfo(String filePath, String userName, Date editDate, String hostName, String path) {
    myFilePath = filePath;
    myUserName = userName;
    myEditDate = editDate;
    myHostName = hostName;
    myPath = path;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EditorInfo)) return false;

    final EditorInfo editorInfo = (EditorInfo)o;

    if (!myEditDate.equals(editorInfo.myEditDate)) return false;
    if (!myHostName.equals(editorInfo.myHostName)) return false;
    if (!myPath.equals(editorInfo.myPath)) return false;
    if (!myUserName.equals(editorInfo.myUserName)) return false;

    return true;
  }

  public int hashCode() {
    int result = myUserName.hashCode();
    result = 29 * result + myEditDate.hashCode();
    result = 29 * result + myHostName.hashCode();
    result = 29 * result + myPath.hashCode();
    return result;
  }

  public String toString() {
    return myUserName + '\t' + DATE_FORMAT.format(myEditDate) + '\t' + myHostName + '\t' + myPath + '\t';
  }

  public String getUserName() { return myUserName; }

  public String getHostName() { return myHostName; }

  public String getPath() { return myPath; }

  public Date getEditDate() { return myEditDate; }

  public String getFilePath() { return myFilePath; }

  private static Date parse(String s) {
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
