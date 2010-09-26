/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.Clock;
import com.intellij.util.text.DateFormatUtil;
import junit.framework.TestCase;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class DateFormatUtilTest extends TestCase{
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy hh.mm.ss");

  public void testPrettyDate() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 0);

    doTestDate("Today", "10.12.2004 17.00.00");
    doTestDate("Today", "10.12.2004 00.00.00");
    doTestDate("Yesterday", "09.12.2004 23.59.59");
    doTestDate("12/8/04", "08.12.2004 23.59.59");

    doTestDate("12/10/03", "10.12.2003 17.00.00");
  }

  public void testPrettyDateTime() throws ParseException {
    Clock.setTime(2004, 11, 10, 17, 0);

    doTestDateTime("Moments ago", "10.12.2004 16.59.31");
    doTestDateTime("A minute ago", "10.12.2004 16.59.29");
    doTestDateTime("5 minutes ago", "10.12.2004 16.55.00");
    doTestDateTime("1 hour ago", "10.12.2004 16.00.00");
    doTestDateTime("Today 3:55 PM", "10.12.2004 15.55.00");
    doTestDateTime("Yesterday 3:00 PM", "09.12.2004 15.00.00");

    doTestDateTime("12/8/04 3:00 PM", "08.12.2004 15.00.00");
    doTestDateTime("12/7/04 3:00 PM", "07.12.2004 15.00.00");

    Clock.setTime(2004, 0, 1, 15, 53);
    doTestDateTime("1/1/03 3:53 PM", "01.01.2003 15.53.00");
    doTestDateTime("Yesterday 3:00 PM", "31.12.2003 15.00.00");
  }

  private void doTestDate(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatPrettyDate(DATE_FORMAT.parse(date)));
  }

  private void doTestDateTime(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatPrettyDateTime(DATE_FORMAT.parse(date)));
  }
}
