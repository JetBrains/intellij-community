/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.sqlite.date;

import java.text.FieldPosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * DatePrinter is the "missing" interface for the format methods of {@link java.text.DateFormat}.
 *
 * @since 3.2
 */
interface DatePrinter {

  /**
   * Formats a millisecond {@code long} value.
   *
   * @param millis the millisecond value to format
   * @return the formatted string
   * @since 2.1
   */
  String format(long millis);

  /**
   * Formats a {@code Date} object using a {@code GregorianCalendar}.
   *
   * @param date the date to format
   * @return the formatted string
   */
  String format(Date date);

  /**
   * Formats a {@code Calendar} object.
   *
   * @param calendar the calendar to format
   * @return the formatted string
   */
  String format(Calendar calendar);

  /**
   * Formats a millisecond {@code long} value into the supplied {@code StringBuffer}.
   *
   * @param millis the millisecond value to format
   * @param buf    the buffer to format into
   * @return the specified string buffer
   */
  StringBuffer format(long millis, StringBuffer buf);

  /**
   * Formats a {@code Date} object into the supplied {@code StringBuffer} using a {@code
   * GregorianCalendar}.
   *
   * @param date the date to format
   * @param buf  the buffer to format into
   * @return the specified string buffer
   */
  StringBuffer format(Date date, StringBuffer buf);

  /**
   * Formats a {@code Calendar} object into the supplied {@code StringBuffer}.
   *
   * @param calendar the calendar to format
   * @param buf      the buffer to format into
   * @return the specified string buffer
   */
  StringBuffer format(Calendar calendar, StringBuffer buf);

  // Accessors
  // -----------------------------------------------------------------------

  /**
   * Gets the pattern used by this printer.
   *
   * @return the pattern, {@link java.text.SimpleDateFormat} compatible
   */
  String getPattern();

  /**
   * Gets the time zone used by this printer.
   *
   * <p>This zone is always used for {@code Date} printing.
   *
   * @return the time zone
   */
  TimeZone getTimeZone();

  /**
   * Gets the locale used by this printer.
   *
   * @return the locale
   */
  Locale getLocale();

  /**
   * Formats a {@code Date}, {@code Calendar} or {@code Long} (milliseconds) object. See {@link
   * java.text.DateFormat#format(Object, StringBuffer, FieldPosition)}
   *
   * @param obj        the object to format
   * @param toAppendTo the buffer to append to
   * @param pos        the position - ignored
   * @return the buffer passed in
   */
  StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos);
}
