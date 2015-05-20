/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;

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

  @NonNls private static final String FORMAT = "EEE MMM dd HH:mm:ss yyyy zzz";
  public final static SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(FORMAT, Locale.US));
  @NonNls private static final String FORMAT1 = "dd MMM yyyy HH:mm:ss zzz";
  public final static SyncDateFormat DATE_FORMAT1 = new SyncDateFormat(new SimpleDateFormat(FORMAT1, Locale.US));
  static {
    DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    DATE_FORMAT1.setTimeZone(TimeZone.getTimeZone("GMT"));
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
