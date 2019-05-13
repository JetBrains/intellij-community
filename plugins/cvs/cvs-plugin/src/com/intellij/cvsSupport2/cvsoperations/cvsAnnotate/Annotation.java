/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsAnnotate;

import com.intellij.util.text.DateFormatUtil;
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
  private String myUser;
  private Date myDate;

  @NonNls private static final String DATE_FORMAT_STRING = "dd-MMM-yy";
  private final static SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat(DATE_FORMAT_STRING, Locale.US));
  public static final String CONTENT_SEPARATOR = ": ";

  public static Annotation createOnMessage(String message) throws ParseException {
    final int firstWhiteSpace = message.indexOf(" ");
    final String revision = message.substring(0, firstWhiteSpace);
    final String tail = message.substring(firstWhiteSpace + 1);
    final int endOfDatePosition = tail.indexOf(CONTENT_SEPARATOR);
    final String date = tail.substring(endOfDatePosition - DATE_FORMAT_STRING.length() - 1, endOfDatePosition - 1);
    final String userWithLeftParentheses = tail.substring(0, endOfDatePosition - DATE_FORMAT_STRING.length() - 1 - 1).trim();
    final String user = userWithLeftParentheses.substring(1);
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
    return DateFormatUtil.formatPrettyDate(getDate());
  }

  public static String createMessageOn(String message) {
    final int index = message.indexOf(CONTENT_SEPARATOR);
    if (index < 0) return "";
    return message.substring(index + CONTENT_SEPARATOR.length()).replaceAll("\r", "");
  }

  public void setUser(String user) {
    myUser = user;
  }

  public void setDate(Date date) {
    myDate = date;
  }
}
