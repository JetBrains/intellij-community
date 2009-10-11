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

import com.intellij.util.text.DateFormatUtil;
import junit.framework.TestCase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateFormatUtilTest extends TestCase{
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy hh.mm.ss");
  private Date myCurrentDate;

  protected void setUp() throws Exception {
    super.setUp();
  }

  public void test() throws ParseException {
    myCurrentDate = DATE_FORMAT.parse("10.12.2004 15.53.27");

    doTest("Today 3:00 PM", "10.12.2004 15.00.00");
    doTest("Yesterday 3:00 PM", "09.12.2004 15.00.00");

    doTest("12/8/04 3:00 PM", "08.12.2004 15.00.00");
    doTest("12/7/04 3:00 PM", "07.12.2004 15.00.00");

    myCurrentDate = DATE_FORMAT.parse("01.01.2004 15.53.27");
    
    doTest("Yesterday 3:00 PM", "31.12.2003 15.00.00");
  }

  private void doTest(String expected, String date) throws ParseException {
    assertEquals(expected, DateFormatUtil.formatDate(myCurrentDate, DATE_FORMAT.parse(date), Locale.US));
  }
}
