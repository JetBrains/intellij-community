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

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;

/**
 * DateParser is the "missing" interface for the parsing methods of {@link java.text.DateFormat}.
 *
 * @since 3.2
 */
 interface DateParser {
  /**
   * Equivalent to DateFormat.parse(String).
   *
   * <p>See {@link java.text.DateFormat#parse(String)} for more information.
   *
   * @param source A <code>String</code> whose beginning should be parsed.
   * @return A <code>Date</code> parsed from the string
   * @throws ParseException if the beginning of the specified string cannot be parsed.
   */
  Date parse(String source) throws ParseException;

  /**
   * Equivalent to DateFormat.parse(String, ParsePosition).
   *
   * <p>See {@link java.text.DateFormat#parse(String, ParsePosition)} for more information.
   *
   * @param source A <code>String</code>, part of which should be parsed.
   * @param pos    A <code>ParsePosition</code> object with index and error index information as
   *               described above.
   * @return A <code>Date</code> parsed from the string. In case of error, returns null.
   * @throws NullPointerException if a text or pos is null.
   */
  Date parse(String source, ParsePosition pos);

  // Accessors
  // -----------------------------------------------------------------------

  /**
   * Get the pattern used by this parser.
   *
   * @return the pattern, {@link java.text.SimpleDateFormat} compatible
   */
  String getPattern();

  /**
   * Parse a date/time string according to the given parse position.
   *
   * @param source A <code>String</code> whose beginning should be parsed.
   * @param pos    the parse position
   * @return a <code>java.util.Date</code> object
   * @see java.text.DateFormat#parseObject(String, ParsePosition)
   */
  Object parseObject(String source, ParsePosition pos);
}
