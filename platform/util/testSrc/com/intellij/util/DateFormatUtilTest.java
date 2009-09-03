/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
