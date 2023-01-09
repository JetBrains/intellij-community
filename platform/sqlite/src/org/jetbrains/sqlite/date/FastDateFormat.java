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

import java.io.Serial;
import java.text.*;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * FastDateFormat is a fast and thread-safe version of {@link SimpleDateFormat}.
 *
 * <p>Since FastDateFormat is thread safe, you can use a static member instance: <code>
 * private static final FastDateFormat DATE_FORMATTER = FastDateFormat.getDateTimeInstance(FastDateFormat.LONG, FastDateFormat.SHORT);
 * </code>
 *
 * <p>This class can be used as a direct replacement to {@code SimpleDateFormat} in most formatting
 * and parsing situations.
 * This class is especially useful in multi-thread server environments.
 * {@code SimpleDateFormat} is not thread-safe in any JDK version, nor will it be as Sun have closed
 * the bug/RFE.
 *
 * <p>All patterns are compatible with SimpleDateFormat (except time zones and some year patterns -
 * see below).
 *
 * <p>Since 3.2, FastDateFormat supports parsing as well as printing.
 *
 * <p>Java 1.4 introduced a new pattern letter, {@code 'Z'}, to represent time zones in RFC822
 * format (e.g. {@code +0800} or {@code -1100}).
 * This pattern letter can be used here (on all JDK
 * versions).
 *
 * <p>In addition, the pattern {@code 'ZZ'} has been made to represent ISO 8601 full-format time
 * zones (eg.
 * or {@code -11:00}).
 * This introduces a minor incompatibility with Java
 * 1.4, but at a gain of useful functionality.
 *
 * <p>Javadoc cites for the year pattern: <i>For formatting, if the number of pattern letters is 2,
 * the year is truncated to 2 digits; otherwise it is interpreted as a number.</i> Starting with
 * Java 1.7 a pattern of 'Y' or 'YYY' will be formatted as '2003', while it was '03' in former Java
 * versions.
 * FastDateFormat implements the behavior of Java 7.
 *
 * @version $Id$
 * @since 2.0
 */
public final class FastDateFormat extends Format implements DateParser, DatePrinter {
  /**
   * Required for serialization support.
   *
   * @see java.io.Serializable
   */
  @Serial private static final long serialVersionUID = 2L;
  private static final FormatCache<FastDateFormat> cache =
    new FormatCache<>() {
      @Override
      protected FastDateFormat createInstance(
        final String pattern, final TimeZone timeZone, final Locale locale) {
        return new FastDateFormat(pattern, timeZone, locale);
      }
    };

  private final FastDatePrinter printer;
  private final FastDateParser parser;

  // -----------------------------------------------------------------------

  /**
   * Constructs a new FastDateFormat.
   *
   * @param pattern  {@link SimpleDateFormat} compatible pattern
   * @param timeZone non-null time zone to use
   * @param locale   non-null locale to use
   * @throws NullPointerException if pattern, timeZone, or locale is null.
   */
  private FastDateFormat(final String pattern, final TimeZone timeZone, final Locale locale) {
    this(pattern, timeZone, locale, null);
  }

  /**
   * Constructs a new FastDateFormat.
   *
   * @param pattern      {@link SimpleDateFormat} compatible pattern
   * @param timeZone     non-null time zone to use
   * @param locale       non-null locale to use
   * @param centuryStart The start of the 100-year period to use as the "default century" for 2
   *                     digit year parsing. If centuryStart is null, defaults to now - 80 years
   * @throws NullPointerException if pattern, timeZone, or locale is null.
   */
  private FastDateFormat(
    final String pattern,
    final TimeZone timeZone,
    final Locale locale,
    final Date centuryStart) {
    printer = new FastDatePrinter(pattern, timeZone, locale);
    parser = new FastDateParser(pattern, timeZone, locale, centuryStart);
  }

  /**
   * Formats a {@code Date}, {@code Calendar} or {@code Long} (milliseconds) object.
   *
   * @param obj        the object to format
   * @param toAppendTo the buffer to append to
   * @param pos        the position - ignored
   * @return the buffer passed in
   */
  @Override
  public StringBuffer format(
    final Object obj, final StringBuffer toAppendTo, final FieldPosition pos) {
    return printer.format(obj, toAppendTo, pos);
  }

  /**
   * Formats a millisecond {@code long} value.
   *
   * @param millis the millisecond value to format
   * @return the formatted string
   * @since 2.1
   */
  @Override
  public String format(final long millis) {
    return printer.format(millis);
  }

  /**
   * Formats a {@code Date} object using a {@code GregorianCalendar}.
   *
   * @param date the date to format
   * @return the formatted string
   */
  @Override
  public String format(final Date date) {
    return printer.format(date);
  }

  // -----------------------------------------------------------------------

  /**
   * Formats a {@code Calendar} object.
   *
   * @param calendar the calendar to format
   * @return the formatted string
   */
  @Override
  public String format(final Calendar calendar) {
    return printer.format(calendar);
  }

  /**
   * Formats a millisecond {@code long} value into the supplied {@code StringBuffer}.
   *
   * @param millis the millisecond value to format
   * @param buf    the buffer to format into
   * @return the specified string buffer
   * @since 2.1
   */
  @Override
  public StringBuffer format(final long millis, final StringBuffer buf) {
    return printer.format(millis, buf);
  }

  /**
   * Formats a {@code Date} object into the supplied {@code StringBuffer} using a {@code
   * GregorianCalendar}.
   *
   * @param date the date to format
   * @param buf  the buffer to format into
   * @return the specified string buffer
   */
  @Override
  public StringBuffer format(final Date date, final StringBuffer buf) {
    return printer.format(date, buf);
  }

  /**
   * Formats a {@code Calendar} object into the supplied {@code StringBuffer}.
   *
   * @param calendar the calendar to format
   * @param buf      the buffer to format into
   * @return the specified string buffer
   */
  @Override
  public StringBuffer format(final Calendar calendar, final StringBuffer buf) {
    return printer.format(calendar, buf);
  }

  // -----------------------------------------------------------------------

  /* (non-Javadoc)
   * @see DateParser#parse(java.lang.String)
   */
  @Override
  public Date parse(final String source) throws ParseException {
    return parser.parse(source);
  }

  /* (non-Javadoc)
   * @see DateParser#parse(java.lang.String, java.text.ParsePosition)
   */
  @Override
  public Date parse(final String source, final ParsePosition pos) {
    return parser.parse(source, pos);
  }

  /* (non-Javadoc)
   * @see java.text.Format#parseObject(java.lang.String, java.text.ParsePosition)
   */
  @Override
  public Object parseObject(final String source, final ParsePosition pos) {
    return parser.parseObject(source, pos);
  }

  /**
   * Gets the pattern used by this formatter.
   *
   * @return the pattern, {@link SimpleDateFormat} compatible
   */
  @Override
  public String getPattern() {
    return printer.getPattern();
  }

  // -----------------------------------------------------------------------

  /**
   * Gets the time zone used by this formatter.
   *
   * <p>This zone is always used for {@code Date} formatting.
   *
   * @return the time zone
   */
  @Override
  public TimeZone getTimeZone() {
    return printer.getTimeZone();
  }

  /**
   * Gets the locale used by this formatter.
   *
   * @return the locale
   */
  @Override
  public Locale getLocale() {
    return printer.getLocale();
  }

  /**
   * Compares two objects for equality.
   *
   * @param obj the object to compare to
   * @return {@code true} if equal
   */
  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof FastDateFormat)) {
      return false;
    }
    final FastDateFormat other = (FastDateFormat)obj;
    // no need to check parser, as it has same invariants as printer
    return printer.equals(other.printer);
  }

  // Constructor
  // -----------------------------------------------------------------------

  /**
   * Returns a hashcode compatible with equals.
   *
   * @return a hashcode compatible with equals
   */
  @Override
  public int hashCode() {
    return printer.hashCode();
  }

  // Constructor
  // -----------------------------------------------------------------------

  /**
   * Gets a debugging string version of this formatter.
   *
   * @return a debugging string
   */
  @Override
  public String toString() {
    return "FastDateFormat["
           + printer.getPattern()
           + ","
           + printer.getLocale()
           + ","
           + printer.getTimeZone().getID()
           + "]";
  }

  // Format methods
  // -----------------------------------------------------------------------

  /**
   * Gets a formatter instance using the default pattern in the default locale.
   *
   * @return a date/time formatter
   */
  public static FastDateFormat getInstance() {
    return cache.getInstance();
  }

  /**
   * Gets a formatter instance using the specified pattern in the default locale.
   *
   * @param pattern {@link SimpleDateFormat} compatible pattern
   * @return a pattern-based date/time formatter
   * @throws IllegalArgumentException if a pattern is invalid
   */
  public static FastDateFormat getInstance(final String pattern) {
    return cache.getInstance(pattern, null, null);
  }

  /**
   * Gets a formatter instance using the specified pattern and time zone.
   *
   * @param pattern  {@link SimpleDateFormat} compatible pattern
   * @param timeZone optional time zone, overrides time zone of formatted date
   * @return a pattern-based date/time formatter
   * @throws IllegalArgumentException if a pattern is invalid
   */
  public static FastDateFormat getInstance(final String pattern, final TimeZone timeZone) {
    return cache.getInstance(pattern, timeZone, null);
  }

  /**
   * Gets a formatter instance using the specified pattern and locale.
   *
   * @param pattern {@link SimpleDateFormat} compatible pattern
   * @param locale  optional locale, overrides system locale
   * @return a pattern-based date/time formatter
   * @throws IllegalArgumentException if a pattern is invalid
   */
  public static FastDateFormat getInstance(final String pattern, final Locale locale) {
    return cache.getInstance(pattern, null, locale);
  }

  /**
   * Gets a formatter instance using the specified pattern, time zone and locale.
   *
   * @param pattern  {@link SimpleDateFormat} compatible pattern
   * @param timeZone optional time zone, overrides time zone of formatted date
   * @param locale   optional locale, overrides system locale
   * @return a pattern-based date/time formatter
   * @throws IllegalArgumentException if a pattern is invalid or {@code null}
   */
  public static FastDateFormat getInstance(
    final String pattern, final TimeZone timeZone, final Locale locale) {
    return cache.getInstance(pattern, timeZone, locale);
  }
}
