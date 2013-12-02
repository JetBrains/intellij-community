/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateFormatUtilTest {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy hh.mm.ss");

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
  public void testTime() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 10, 15);

    if (SystemInfo.isMac) {
      assertEquals("17:10", DateFormatUtil.formatTime(Clock.getTime()));
      assertEquals("17:10:15", DateFormatUtil.formatTimeWithSeconds(Clock.getTime()));
      assertEquals("17:10:15", DateFormatUtil.formatTimeWithSeconds(new Date(Clock.getTime())));
    }
    else {
      assertEquals(DateFormat.getTimeInstance(DateFormat.SHORT).format(Clock.getTime()),
                   DateFormatUtil.formatTime(Clock.getTime()));
      assertEquals(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Clock.getTime()),
                   DateFormatUtil.formatTimeWithSeconds(Clock.getTime()));
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

    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("08.12.2004 15.00.00")), "08.12.2004 15.00.00");
    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("07.12.2004 15.00.00")), "07.12.2004 15.00.00");

    doTestDateTime("Today " + DateFormatUtil.formatTime(DATE_FORMAT.parse("10.12.2004 19.00.00")), "10.12.2004 19.00.00");

    Clock.setTime(2004, 0, 1, 15, 53);
    doTestDateTime(DateFormatUtil.formatDateTime(DATE_FORMAT.parse("01.01.2003 15.53.00")), "01.01.2003 15.53.00");
    doTestDateTime("Yesterday " + DateFormatUtil.formatTime(DATE_FORMAT.parse("31.12.2003 15.00.00")), "31.12.2003 15.00.00");
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

  @Test
  public void testConvertingMacToJavaPattern() throws Throwable {
    Clock.setTime(date(2004, 2, 5, 16, 6, 7).getTime() + 8);

    String mon = new SimpleDateFormat("MMM").format(Clock.getTime());
    String month = new SimpleDateFormat("MMMMM").format(Clock.getTime());
    String weekd = new SimpleDateFormat("EEE").format(Clock.getTime());
    String weekday = new SimpleDateFormat("EEEEE").format(Clock.getTime());

    assertConvertedFormat("%y %Y", "04 2004");
    assertConvertedFormat("%b %B %m", mon + " " + month + " 02");
    assertConvertedFormat("%d %e %j", "05 5 036");
    assertConvertedFormat("%a %A %w", weekd + " " + weekday + " " + weekd);

    assertConvertedFormat("%H %I", "16 04");
    assertConvertedFormat("%M %S %F %p", "06 07 008 PM");

    assertConvertedFormatMatches("%z %Z", "\\+\\d{4} \\w{3}");

    assertConvertedFormat(" foo bar ", " foo bar ");
    assertConvertedFormat(" 'foo''a'a'' '' ' ", " 'foo''a'a'' '' ' ");
    assertConvertedFormat(" '%a''%a'%a'' '' ' '%a ", " '" + weekd + "''" + weekd + "'" + weekd + "'' '' ' '" + weekd + " ");
    assertConvertedFormat("'a'", "'a'");
    assertConvertedFormat("'", "'");
    assertConvertedFormat("''", "''");
    assertConvertedFormat("a", "a");
    assertConvertedFormat(" ", " ");
    assertConvertedFormat("%1", "?%1?");
    assertConvertedFormat("", "");

    assertConvertedFormat("%", "");
  }

  private static void assertConvertedFormat(String pattern, String expected) throws Throwable {
    String converted = convertMacPattern(pattern);
    try {
      assertEquals(expected, new SimpleDateFormat(converted).format(Clock.getTime()));
    }
    catch (Throwable e) {
      System.out.println("cannot format with [" + converted + "]");
      throw e;
    }
  }

  private static void assertConvertedFormatMatches(String pattern, String expectedPattern) throws Throwable {
    String converted = convertMacPattern(pattern);
    try {
      String actual = new SimpleDateFormat(converted).format(Clock.getTime());
      assertTrue(actual, actual.matches(expectedPattern));
    }
    catch (Throwable e) {
      System.out.println("cannot format with [" + converted + "]");
      throw e;
    }
  }

  private static String convertMacPattern(String macPattern) {
    StringBuilder b = new StringBuilder();
    boolean isSpecial = false;
    boolean isText = false;

    for (int i = 0; i < macPattern.length(); i++) {
      char c = macPattern.charAt(i);
      if (isSpecial) {
        String replacement = null;
        if (c == '%') replacement = "$";

        // year
        if (c == 'y') replacement = "yy";
        if (c == 'Y') replacement = "yyyy";

        // month
        if (c == 'm') replacement = "MM";
        if (c == 'b') replacement = "MMM";
        if (c == 'B') replacement = "MMMMM";

        // day on month
        if (c == 'e') replacement = "d";
        if (c == 'd') replacement = "dd";

        // day of year
        if (c == 'j') replacement = "DDD";

        // day of week
        if (c == 'w') replacement = "E"; // SimpleDateFormat doesn't support formatting weekday as a number
        if (c == 'a') replacement = "EEE";
        if (c == 'A') replacement = "EEEEE";

        // hours
        if (c == 'H') replacement = "HH"; // 0-24
        //if (c == 'H') replacement = "k"; // 1-24
        //if (c == 'I') replacement = "K"; // 0-11
        if (c == 'I') replacement = "hh"; // 1-12

        //minute
        if (c == 'M') replacement = "mm";
        //second
        if (c == 'S') replacement = "ss";
        //millisecond
        if (c == 'F') replacement = "SSS";

        //millisecond
        if (c == 'p') replacement = "a";

        //millisecond
        if (c == 'Z') replacement = "zzz";
        //millisecond
        if (c == 'z') replacement = "Z";

        //todo if (c == 'c') replacement = "MMMMM";, x, X

        if (replacement == null) replacement = "'?%" + c + "?'";

        b.append(replacement);
        isSpecial = false;
      }
      else {
        isSpecial = c == '%';
        if (isSpecial) {
          isText = false;
        }
        else {
          if (isText) {
            if (c == '\'' || Character.isWhitespace(c)) b.append('\'');
            isText = !Character.isWhitespace(c);
          }
          else {
            if (c == '\'' || !Character.isWhitespace(c)) b.append('\'');
            isText = !Character.isWhitespace(c) && c != '\'';
          }
          b.append(c);

          if (isText && i == macPattern.length() - 1) b.append('\'');
        }
      }
    }
    return b.toString();
  }

  @Test
  public void testAboutDialogDataFormatter() {
    assertEquals("December 12, 2012",
                 DateFormatUtil.formatAboutDialogDate(date(2012, 12, 12, 15, 35, 12)));
    assertEquals("January 1, 1999",
                 DateFormatUtil.formatAboutDialogDate(date(1999, 1, 1, 0, 0, 0)));
  }

  @Test
  public void testFormatFrequency() {
    assertEquals("Once in 2 minutes",
                 DateFormatUtil.formatFrequency(2 * 60 * 1000));
    assertEquals("Once in a few moments",
                 DateFormatUtil.formatFrequency(1000));
  }
}
