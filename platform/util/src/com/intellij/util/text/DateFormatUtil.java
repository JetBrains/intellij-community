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
package com.intellij.util.text;

import com.intellij.CommonBundle;
import com.intellij.openapi.util.Clock;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateFormatUtil {
  // do not expose this constants - they are very likely to be changed in future
  private static final DateFormat DATE_FORMAT = DateFormat.getDateInstance(DateFormat.SHORT);
  private static final DateFormat TIME_FORMAT = DateFormat.getTimeInstance(DateFormat.SHORT);
  private static final DateFormat DATE_TIME_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

  public static final long SECOND = 1000;
  public static final long MINUTE = SECOND * 60;
  public static final long HOUR = MINUTE * 60;
  public static final long DAY = HOUR * 24;
  public static final long WEEK = DAY * 7;
  public static final long MONTH = DAY * 30;
  public static final long YEAR = DAY * 365;
  private static final long[] DELIMS = new long[]{YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};

  private enum Period {
    YEAR, MONTH, WEEK, DAY, HOUR, MINUTE
  }

  private static final Period[] PERIOD = new Period[]{Period.YEAR, Period.MONTH, Period.WEEK, Period.DAY, Period.HOUR, Period.MINUTE};

  private DateFormatUtil() {
  }

  @NotNull
  public static DateFormat getDateFormat() {
    return DATE_FORMAT;
  }

  @NotNull
  public static DateFormat getTimeFormat() {
    return TIME_FORMAT;
  }

  @NotNull
  public static DateFormat getDateTimeFormat() {
    return DATE_TIME_FORMAT;
  }

  @NotNull
  public static String formatTime(long time) {
    return getTimeFormat().format(time);
  }

  @NotNull
  public static String formatDate(long time) {
    return getDateFormat().format(time);
  }

  @NotNull
  public static String formatExactDateTime(long time) {
    return getDateTimeFormat().format(time);
  }

  @NotNull
  public static String formatDateTime(Date time) {
    return formatDateTime(time.getTime());
  }

  @NotNull
  public static String formatDateTime(long time) {
    return formatDateTime(time, DATE_TIME_FORMAT, TIME_FORMAT);
  }

  @NotNull
  public static String formatDateTime(long time, @NotNull DateFormat dateTimeFormat, @NotNull DateFormat timeFormat) {
    long currentTime = Clock.getTime();

    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(currentTime);

    int currentYear = c.get(Calendar.YEAR);
    int currentDayOfYear = c.get(Calendar.DAY_OF_YEAR);

    c.setTimeInMillis(time);

    int year = c.get(Calendar.YEAR);
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);

    long delta = currentTime - time;
    if (delta <= HOUR) {
      return CommonBundle.message("date.format.minutes.ago", (int)Math.rint(delta / (double)MINUTE));
    }

    if (currentDayOfYear == dayOfYear) {
      return CommonBundle.message("date.format.today") + " " + timeFormat.format(time);
    }

    boolean isYesterdayOnPreviousYear =
      (currentYear == year + 1) && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(Calendar.DAY_OF_YEAR);
    boolean isYesterday = isYesterdayOnPreviousYear || (currentYear == year && currentDayOfYear == dayOfYear + 1);

    if (isYesterday) {
      return CommonBundle.message("date.format.yesterday") + " " + timeFormat.format(time);
    }

    return dateTimeFormat.format(time);
  }

  @NotNull
  public static String formatDuration(long delta) {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < DELIMS.length; i++) {
      long delim = DELIMS[i];
      int n = (int)(delta / delim);
      if (n != 0) {
        buf.append(composeDurationMessage(PERIOD[i], n));
        buf.append(' ');
        delta = delta % delim;
      }
    }

    if (buf.length() == 0) return CommonBundle.message("date.format.less.than.a.minute");
    return buf.toString().trim();
  }

  private static String composeDurationMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return CommonBundle.message("date.format.n.days", n);
      case MINUTE:
        return CommonBundle.message("date.format.n.minutes", n);
      case HOUR:
        return CommonBundle.message("date.format.n.hours", n);
      case MONTH:
        return CommonBundle.message("date.format.n.months", n);
      case WEEK:
        return CommonBundle.message("date.format.n.weeks", n);
      default:
        return CommonBundle.message("date.format.n.years", n);
    }
  }

  @NotNull
  public static String formatFrequency(long time) {
    return CommonBundle.message("date.frequency", formatBetweenDates(time, 0));
  }

  @NotNull
  public static String formatBetweenDates(long d1, long d2) {
    long delta = Math.abs(d1 - d2);
    if (delta == 0) return CommonBundle.message("date.format.right.now");

    int n = -1;
    int i;
    for (i = 0; i < DELIMS.length; i++) {
      long delim = DELIMS[i];
      if (delta >= delim) {
        n = (int)(delta / delim);
        break;
      }
    }

    if (d2 > d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.a.few.moments.ago");
      }
      else {
        return someTimeAgoMessage(PERIOD[i], n);
      }
    }
    else if (d2 < d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.in.a.few.moments");
      }
      else {
        return composeInSomeTimeMessage(PERIOD[i], n);
      }
    }

    return "";
  }

  private static String someTimeAgoMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return CommonBundle.message("date.format.n.days.ago", n);
      case MINUTE:
        return CommonBundle.message("date.format.n.minutes.ago", n);
      case HOUR:
        return CommonBundle.message("date.format.n.hours.ago", n);
      case MONTH:
        return CommonBundle.message("date.format.n.months.ago", n);
      case WEEK:
        return CommonBundle.message("date.format.n.weeks.ago", n);
      default:
        return CommonBundle.message("date.format.n.years.ago", n);
    }
  }

  private static String composeInSomeTimeMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return CommonBundle.message("date.format.in.n.days", n);
      case MINUTE:
        return CommonBundle.message("date.format.in.n.minutes", n);
      case HOUR:
        return CommonBundle.message("date.format.in.n.hours", n);
      case MONTH:
        return CommonBundle.message("date.format.in.n.months", n);
      case WEEK:
        return CommonBundle.message("date.format.in.n.weeks", n);
      default:
        return CommonBundle.message("date.format.in.n.years", n);
    }
  }
}