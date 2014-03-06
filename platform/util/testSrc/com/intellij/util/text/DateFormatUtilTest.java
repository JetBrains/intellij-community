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
package com.intellij.util.text;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DateFormatUtilTest {
  @SuppressWarnings("SpellCheckingInspection") private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy hh.mm.ss");

  @Test
  public void testBasics() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 10);

    doTestPrettyDate("Today", "10.12.2004 17.00.00");
    doTestPrettyDate("Today", "10.12.2004 00.00.00");
    doTestPrettyDate("Yesterday", "09.12.2004 23.59.59");
    doTestPrettyDate(DateFormatUtil.formatDate(DATE_FORMAT.parse("08.12.2004 23.59.59")), "08.12.2004 23.59.59");
    doTestPrettyDate(DateFormatUtil.formatDate(DATE_FORMAT.parse("10.12.2003 17.00.00")), "10.12.2003 17.00.00");
  }

  @Test
  public void testTime() throws Exception {
    Clock.setTime(2004, 11, 10, 17, 10, 15);

    if (SystemInfo.isMac) {
      assertEquals("17:10", DateFormatUtil.formatTime(Clock.getTime()));
      assertEquals("17:10:15", DateFormatUtil.formatTimeWithSeconds(Clock.getTime()));
    }
    else if (SystemInfo.isUnix) {
      assertEquals("5:10:15 PM", printTimeForLocale("en_US.UTF-8"));
      assertEquals("17:10:15", printTimeForLocale("de_DE.UTF-8"));
    }
    else if (SystemInfo.isWinVistaOrNewer) {
      String[] system = getWindowsTime();
      Date now = new Date();
      assertEquals(system[0], DateFormatUtil.formatDate(now));
      assertEquals(system[1], DateFormatUtil.formatTimeWithSeconds(now));
    }
    else {
      assertEquals(DateFormat.getTimeInstance(DateFormat.SHORT).format(Clock.getTime()),
                   DateFormatUtil.formatTime(Clock.getTime()));
      assertEquals(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Clock.getTime()),
                   DateFormatUtil.formatTimeWithSeconds(new Date(Clock.getTime())));
    }
  }

  @Test
  public void testPrettyDateTime() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 0);
    doTestDateTime("Moments ago", "10.12.2004 16.59.31");
    doTestDateTime("A minute ago", "10.12.2004 16.59.29");
    doTestDateTime("5 minutes ago", "10.12.2004 16.55.00");
    doTestDateTime("1 hour ago", "10.12.2004 16.00.00");
    doTestDateTime("Today " + DateFormatUtil.formatTime(DATE_FORMAT.parse("10.12.2004 15.55.00")), "10.12.2004 15.55.00");
    doTestDateTime("Yesterday " + DateFormatUtil.formatTime(DATE_FORMAT.parse("09.12.2004 15.00.00")), "09.12.2004 15.00.00");
    doTestDateTime("Today " + DateFormatUtil.formatTime(DATE_FORMAT.parse("10.12.2004 19.00.00")), "10.12.2004 19.00.00");

    Clock.setTime(2004, 0, 1, 15, 53);
    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("01.01.2003 15.53.00")), "01.01.2003 15.53.00");
    doTestDateTime("Yesterday " + DateFormatUtil.formatTime(DATE_FORMAT.parse("31.12.2003 15.00.00")), "31.12.2003 15.00.00");
  }

  @Test
  public void testAboutDialogDataFormatter() {
    assertEquals("December 12, 2012", DateFormatUtil.formatAboutDialogDate(date(2012, 12, 12, 15, 35, 12)));
    assertEquals("January 1, 1999", DateFormatUtil.formatAboutDialogDate(date(1999, 1, 1, 0, 0, 0)));
  }

  @Test
  public void testFormatFrequency() {
    assertEquals("Once in 2 minutes", DateFormatUtil.formatFrequency(2 * 60 * 1000));
    assertEquals("Once in a few moments", DateFormatUtil.formatFrequency(1000));
  }

  private static void doTestPrettyDate(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatPrettyDate(DATE_FORMAT.parse(date)));
  }

  private static void doTestDateTime(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatPrettyDateTime(DATE_FORMAT.parse(date)));
  }

  private static Date date(final int year, final int month, final int day, final int hour, final int minute, final int second) {
    return new GregorianCalendar(year, month - 1, day, hour, minute, second).getTime();
  }

  private static String printTimeForLocale(String locale) throws IOException {
    List<String> classpath = ContainerUtil.newArrayList();
    classpath.addAll(PathManager.getUtilClassPath());
    classpath.add(PathManager.getJarPathForClass(PrintTime.class));
    ProcessBuilder builder = new ProcessBuilder()
      .command(System.getProperty("java.home") + "/bin/java",
               "-classpath",
               StringUtil.join(classpath, File.pathSeparator),
               PrintTime.class.getName(),
               String.valueOf(Clock.getTime()))
      .redirectErrorStream(true);
    builder.environment().put("LC_TIME", locale);
    Process process = builder.start();

    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    try {
      return reader.readLine();
    }
    finally {
      reader.close();
    }
  }

  private static String[] getWindowsTime() throws IOException {
    Process process = new ProcessBuilder("cmd", "/c", "echo %DATE%@%TIME%").start();

    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    try {
      String datetime = reader.readLine();
      if (datetime.matches(".+[.,]\\d\\d")) {
        datetime = datetime.substring(0, datetime.length() - 3);
      }
      String[] parts = datetime.split("@");
      assertEquals(2, parts.length);
      return parts;
    }
    finally {
      reader.close();
    }
  }
}
