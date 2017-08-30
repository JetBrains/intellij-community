/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateFormatUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.util.text.DateFormatUtil");

  public static final long SECOND = 1000;
  public static final long MINUTE = SECOND * 60;
  public static final long HOUR = MINUTE * 60;
  public static final long DAY = HOUR * 24;
  public static final long WEEK = DAY * 7;
  public static final long MONTH = DAY * 30;
  public static final long YEAR = DAY * 365;
  public static final long DAY_FACTOR = 24L * 60 * 60 * 1000;

  // do not expose this constants - they are very likely to be changed in future
  private static final SyncDateFormat DATE_FORMAT;
  private static final SyncDateFormat TIME_FORMAT;
  private static final SyncDateFormat TIME_WITH_SECONDS_FORMAT;
  private static final SyncDateFormat DATE_TIME_FORMAT;
  private static final SyncDateFormat ABOUT_DATE_FORMAT;
  private static final SyncDateFormat ISO8601_FORMAT;

  static {
    SyncDateFormat[] formats = getDateTimeFormats();
    DATE_FORMAT = formats[0];
    TIME_FORMAT = formats[1];
    TIME_WITH_SECONDS_FORMAT = formats[2];
    DATE_TIME_FORMAT = formats[3];

    ABOUT_DATE_FORMAT = new SyncDateFormat(DateFormat.getDateInstance(DateFormat.LONG, Locale.US));

    @SuppressWarnings("SpellCheckingInspection") DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    ISO8601_FORMAT = new SyncDateFormat(iso8601);
  }

  private static final long[] DENOMINATORS = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE};
  private enum Period {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE}
  private static final Period[] PERIODS = {Period.YEAR, Period.MONTH, Period.WEEK, Period.DAY, Period.HOUR, Period.MINUTE};

  private DateFormatUtil() { }

  public static long getDifferenceInDays(@NotNull Date startDate, @NotNull Date endDate) {
    return (endDate.getTime() - startDate.getTime() + DAY_FACTOR - 1000) / DAY_FACTOR;
  }

  @NotNull
  public static SyncDateFormat getDateFormat() {
    return DATE_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getTimeFormat() {
    return TIME_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getTimeWithSecondsFormat() {
    return TIME_WITH_SECONDS_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getDateTimeFormat() {
    return DATE_TIME_FORMAT;
  }

  @NotNull
  public static SyncDateFormat getIso8601Format() {
    return ISO8601_FORMAT;
  }

  @NotNull
  public static String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  @NotNull
  public static String formatTime(long time) {
    return getTimeFormat().format(time);
  }

  @NotNull
  public static String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  @NotNull
  public static String formatTimeWithSeconds(long time) {
    return getTimeWithSecondsFormat().format(time);
  }

  @NotNull
  public static String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  @NotNull
  public static String formatDate(long time) {
    return getDateFormat().format(time);
  }

  @NotNull
  public static String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  @NotNull
  public static String formatPrettyDate(long time) {
    return doFormatPretty(time, false);
  }

  @NotNull
  public static String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  @NotNull
  public static String formatDateTime(long time) {
    return getDateTimeFormat().format(time);
  }

  @NotNull
  public static String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  @NotNull
  public static String formatPrettyDateTime(long time) {
    return doFormatPretty(time, true);
  }

  @NotNull
  private static String doFormatPretty(long time, boolean formatTime) {
    long currentTime = Clock.getTime();

    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(currentTime);

    int currentYear = c.get(Calendar.YEAR);
    int currentDayOfYear = c.get(Calendar.DAY_OF_YEAR);

    c.setTimeInMillis(time);

    int year = c.get(Calendar.YEAR);
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);

    if (formatTime) {
      long delta = currentTime - time;
      if (delta <= HOUR && delta >= 0) {
        return CommonBundle.message("date.format.minutes.ago", (int)Math.rint(delta / (double)MINUTE));
      }
    }

    boolean isToday = currentYear == year && currentDayOfYear == dayOfYear;
    if (isToday) {
      String result = CommonBundle.message("date.format.today");
      return formatTime ? result + " " + TIME_FORMAT.format(time) : result;
    }

    boolean isYesterdayOnPreviousYear =
      (currentYear == year + 1) && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(Calendar.DAY_OF_YEAR);
    boolean isYesterday = isYesterdayOnPreviousYear || (currentYear == year && currentDayOfYear == dayOfYear + 1);

    if (isYesterday) {
      String result = CommonBundle.message("date.format.yesterday");
      return formatTime ? result + " " + TIME_FORMAT.format(time) : result;
    }

    return formatTime ? DATE_TIME_FORMAT.format(time) : DATE_FORMAT.format(time);
  }

  @NotNull
  public static String formatDuration(long delta) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < DENOMINATORS.length; i++) {
      long denominator = DENOMINATORS[i];
      int n = (int)(delta / denominator);
      if (n != 0) {
        buf.append(composeDurationMessage(PERIODS[i], n));
        buf.append(' ');
        delta = delta % denominator;
      }
    }

    if (buf.length() == 0) return CommonBundle.message("date.format.less.than.a.minute");
    return buf.toString().trim();
  }

  @SuppressWarnings("Duplicates")
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
    for (i = 0; i < DENOMINATORS.length; i++) {
      long denominator = DENOMINATORS[i];
      if (delta >= denominator) {
        n = (int)(delta / denominator);
        break;
      }
    }

    if (d2 > d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.a.few.moments.ago");
      }
      else {
        return someTimeAgoMessage(PERIODS[i], n);
      }
    }
    else if (d2 < d1) {
      if (n <= 0) {
        return CommonBundle.message("date.format.in.a.few.moments");
      }
      else {
        return composeInSomeTimeMessage(PERIODS[i], n);
      }
    }

    return "";
  }

  @NotNull
  public static String formatAboutDialogDate(@NotNull Date date) {
    return ABOUT_DATE_FORMAT.format(date);
  }

  // helpers

  @SuppressWarnings("Duplicates")
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

  @SuppressWarnings("Duplicates")
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

  private static SyncDateFormat[] getDateTimeFormats() {
    DateFormat[] formats = new DateFormat[4];

    boolean loaded = false;
    try {
      if (SystemInfo.isWin7OrNewer) {
        loaded = getWindowsFormats(formats);
      }
      else if (SystemInfo.isMac) {
        loaded = getMacFormats(formats);
      }
      else if (SystemInfo.isUnix) {
        loaded = getUnixFormats(formats);
      }
    }
    catch (Throwable t) {
      LOG.info(t);
    }

    if (!loaded) {
      formats[0] = DateFormat.getDateInstance(DateFormat.SHORT);
      formats[1] = DateFormat.getTimeInstance(DateFormat.SHORT);
      formats[2] = DateFormat.getTimeInstance(DateFormat.MEDIUM);
      formats[3] = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    }

    SyncDateFormat[] synced = new SyncDateFormat[4];
    for (int i = 0; i < formats.length; i++) {
      synced[i] = new SyncDateFormat(formats[i]);
    }
    return synced;
  }

  private static boolean getMacFormats(DateFormat[] formats) {
    final int MacFormatterNoStyle = 0;
    final int MacFormatterShortStyle = 1;
    final int MacFormatterMediumStyle = 2;
    final int MacFormatterBehavior_10_4 = 1040;

    ID autoReleasePool = Foundation.invoke("NSAutoreleasePool", "new");
    try {
      ID dateFormatter = Foundation.invoke("NSDateFormatter", "new");
      Foundation.invoke(dateFormatter, Foundation.createSelector("setFormatterBehavior:"), MacFormatterBehavior_10_4);

      formats[0] = invokeFormatter(dateFormatter, MacFormatterNoStyle, MacFormatterShortStyle);  // short date
      formats[1] = invokeFormatter(dateFormatter, MacFormatterShortStyle, MacFormatterNoStyle);  // short time
      formats[2] = invokeFormatter(dateFormatter, MacFormatterMediumStyle, MacFormatterNoStyle);  // medium time
      formats[3] = invokeFormatter(dateFormatter, MacFormatterShortStyle, MacFormatterShortStyle);  // short date/time

      return true;
    }
    finally {
      Foundation.invoke(autoReleasePool, Foundation.createSelector("release"));
    }
  }

  private static DateFormat invokeFormatter(ID dateFormatter, int timeStyle, int dateStyle) {
    Foundation.invoke(dateFormatter, Foundation.createSelector("setTimeStyle:"), timeStyle);
    Foundation.invoke(dateFormatter, Foundation.createSelector("setDateStyle:"), dateStyle);
    String format = Foundation.toStringViaUTF8(Foundation.invoke(dateFormatter, Foundation.createSelector("dateFormat")));
    assert format != null;
    return new SimpleDateFormat(format.trim());
  }

  private static boolean getUnixFormats(DateFormat[] formats) {
    String localeStr = System.getenv("LC_TIME");
    if (localeStr == null) return false;

    localeStr = localeStr.trim();
    int p = localeStr.indexOf('.');
    if (p > 0) localeStr = localeStr.substring(0, p);
    p = localeStr.indexOf('@');
    if (p > 0) localeStr = localeStr.substring(0, p);

    Locale locale;
    p = localeStr.indexOf('_');
    if (p < 0) {
      locale = new Locale(localeStr);
    }
    else {
      locale = new Locale(localeStr.substring(0, p), localeStr.substring(p + 1));
    }

    formats[0] = DateFormat.getDateInstance(DateFormat.SHORT, locale);
    formats[1] = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
    formats[2] = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale);
    formats[3] = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);

    return true;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private interface Kernel32 extends StdCallLibrary {
    String LOCALE_NAME_USER_DEFAULT = null;

    int LOCALE_SSHORTDATE  = 0x0000001F;
    int LOCALE_SSHORTTIME  = 0x00000079;
    int LOCALE_STIMEFORMAT = 0x00001003;

    int GetLocaleInfoEx(String localeName, int lcType, Pointer lcData, int dataSize);
    int GetLastError();
  }

  private static boolean getWindowsFormats(DateFormat[] formats) {
    Kernel32 kernel32 = Native.loadLibrary("Kernel32", Kernel32.class);
    int dataSize = 128, rv;
    Memory data = new Memory(dataSize);

    rv = kernel32.GetLocaleInfoEx(Kernel32.LOCALE_NAME_USER_DEFAULT, Kernel32.LOCALE_SSHORTDATE, data, dataSize);
    assert rv > 1 : kernel32.GetLastError();
    String shortDate = fixWindowsFormat(new String(data.getCharArray(0, rv - 1)));

    rv = kernel32.GetLocaleInfoEx(Kernel32.LOCALE_NAME_USER_DEFAULT, Kernel32.LOCALE_SSHORTTIME, data, dataSize);
    assert rv > 1 : kernel32.GetLastError();
    String shortTime = fixWindowsFormat(new String(data.getCharArray(0, rv - 1)));

    rv = kernel32.GetLocaleInfoEx(Kernel32.LOCALE_NAME_USER_DEFAULT, Kernel32.LOCALE_STIMEFORMAT, data, dataSize);
    assert rv > 1 : kernel32.GetLastError();
    String mediumTime = fixWindowsFormat(new String(data.getCharArray(0, rv - 1)));

    formats[0] = new SimpleDateFormat(shortDate);
    formats[1] = new SimpleDateFormat(shortTime);
    formats[2] = new SimpleDateFormat(mediumTime);
    formats[3] = new SimpleDateFormat(shortDate + " " + shortTime);

    return true;
  }

  private static String fixWindowsFormat(String format) {
    format = format.replaceAll("g+", "G");
    format = StringUtil.replace(format, "tt", "a");
    return format;
  }
}