// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.UtilBundle;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateFormatUtil {
  private static final Logger LOG = Logger.getInstance(DateFormatUtil.class);

  public static final long SECOND = 1000;
  public static final long MINUTE = SECOND * 60;
  public static final long HOUR = MINUTE * 60;
  public static final long DAY = HOUR * 24;
  public static final long WEEK = DAY * 7;
  public static final long MONTH = DAY * 30;
  public static final long YEAR = DAY * 365;
  public static final long DAY_FACTOR = 24L * 60 * 60 * 1000;

  // do not expose these constants - they are very likely to be changed in future
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

  public static boolean isPrettyFormattingPossible(long time) {
    return _doFormatPretty(time, true).second;
  }

  private static String doFormatPretty(long time, boolean formatTime) {
    return _doFormatPretty(time, formatTime).first;
  }
  @NotNull
  private static Pair<String, Boolean> _doFormatPretty(long time, boolean formatTime) {
    long currentTime = Clock.getTime();
    Calendar c = Calendar.getInstance();

    c.setTimeInMillis(currentTime);
    int currentYear = c.get(Calendar.YEAR);
    int currentDayOfYear = c.get(Calendar.DAY_OF_YEAR);

    c.setTimeInMillis(time);
    int year = c.get(Calendar.YEAR);
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);

    if (LOG.isTraceEnabled()) {
      LOG.trace("now=" + currentTime + " t=" + time + " z=" + c.getTimeZone());
    }

    if (formatTime) {
      long delta = currentTime - time;
      if (delta >= 0 && delta <= HOUR + MINUTE) {
        return Pair.create(UtilBundle.message("date.format.minutes.ago", (int)Math.rint(delta / (double)MINUTE)), Boolean.TRUE);
      }
    }

    boolean isToday = currentYear == year && currentDayOfYear == dayOfYear;
    if (isToday) {
      String result = UtilBundle.message("date.format.today");
      return Pair.create(formatTime ? result + " " + TIME_FORMAT.format(time) : result, Boolean.TRUE);
    }

    boolean isYesterdayOnPreviousYear =
      (currentYear == year + 1) && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(Calendar.DAY_OF_YEAR);
    boolean isYesterday = isYesterdayOnPreviousYear || (currentYear == year && currentDayOfYear == dayOfYear + 1);
    if (isYesterday) {
      String result = UtilBundle.message("date.format.yesterday");
      return Pair.create(formatTime ? result + " " + TIME_FORMAT.format(time) : result, Boolean.TRUE);
    }

    return Pair.create(formatTime ? DATE_TIME_FORMAT.format(time) : DATE_FORMAT.format(time), Boolean.FALSE);
  }

  @NotNull
  public static String formatFrequency(long time) {
    return UtilBundle.message("date.frequency", formatBetweenDates(time, 0));
  }

  @NotNull
  public static String formatBetweenDates(long d1, long d2) {
    long delta = Math.abs(d1 - d2);
    if (delta == 0) return UtilBundle.message("date.format.right.now");

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
        return UtilBundle.message("date.format.a.few.moments.ago");
      }
      else {
        return someTimeAgoMessage(PERIODS[i], n);
      }
    }
    else if (d2 < d1) {
      if (n <= 0) {
        return UtilBundle.message("date.format.in.a.few.moments");
      }
      else {
        return composeInSomeTimeMessage(PERIODS[i], n);
      }
    }

    return "";
  }

  @NotNull
  public static String formatAboutDialogDate(@NotNull Date date) {
    return formatAboutDialogDate(date.getTime());
  }

  @NotNull
  public static String formatAboutDialogDate(long time) {
    return ABOUT_DATE_FORMAT.format(time);
  }

  /**
   * Return sample date, that can be used to determine preferred string width.
   * <p>
   * We should not use {@code new Date()} to ensure results are reproducible (and to avoid "Today" for pretty formats).
   * Returned date is expected to return maximum width string for date formats like "d.m.yy H:M".
   */
  @NotNull
  public static Date getSampleDateTime() {
    //noinspection deprecation
    return new Date(100, Calendar.DECEMBER, 31, 23, 59);
  }

  //<editor-fold desc="Helpers.">
  private static String someTimeAgoMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return UtilBundle.message("date.format.n.days.ago", n);
      case MINUTE:
        return UtilBundle.message("date.format.n.minutes.ago", n);
      case HOUR:
        return UtilBundle.message("date.format.n.hours.ago", n);
      case MONTH:
        return UtilBundle.message("date.format.n.months.ago", n);
      case WEEK:
        return UtilBundle.message("date.format.n.weeks.ago", n);
      default:
        return UtilBundle.message("date.format.n.years.ago", n);
    }
  }

  private static String composeInSomeTimeMessage(final Period period, final int n) {
    switch (period) {
      case DAY:
        return UtilBundle.message("date.format.in.n.days", n);
      case MINUTE:
        return UtilBundle.message("date.format.in.n.minutes", n);
      case HOUR:
        return UtilBundle.message("date.format.in.n.hours", n);
      case MONTH:
        return UtilBundle.message("date.format.in.n.months", n);
      case WEEK:
        return UtilBundle.message("date.format.in.n.weeks", n);
      default:
        return UtilBundle.message("date.format.in.n.years", n);
    }
  }

  private static SyncDateFormat[] getDateTimeFormats() {
    DateFormat[] formats = null;
    try {
      if (SystemInfo.isMac && JnaLoader.isLoaded()) {
        formats = getMacFormats();
      }
      else if (SystemInfo.isUnix) {
        formats = getUnixFormats();
      }
      else if (SystemInfo.isWin7OrNewer && JnaLoader.isLoaded() ) {
        formats = getWindowsFormats();
      }
    }
    catch (Throwable t) {
      LOG.error(t);
    }
    if (formats == null) {
      formats = new DateFormat[]{
        DateFormat.getDateInstance(DateFormat.SHORT),
        DateFormat.getTimeInstance(DateFormat.SHORT),
        DateFormat.getTimeInstance(DateFormat.MEDIUM),
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
      };
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("formats (OS=" + SystemInfo.OS_NAME + " JNA=" + JnaLoader.isLoaded() + ")");
      for (DateFormat format: formats) {
        LOG.trace("'" + (format instanceof SimpleDateFormat ? ((SimpleDateFormat)format).toPattern() : format.toString()) + "'");
      }
    }

    SyncDateFormat[] synced = new SyncDateFormat[4];
    for (int i = 0; i < formats.length; i++) {
      synced[i] = new SyncDateFormat(formats[i]);
    }
    return synced;
  }

  private interface CF extends Library {
    long kCFDateFormatterNoStyle = 0;
    long kCFDateFormatterShortStyle = 1;
    long kCFDateFormatterMediumStyle = 2;

    @Structure.FieldOrder({"location", "length"})
    class CFRange extends Structure implements Structure.ByValue {
      public long location;
      public long length;

      public CFRange(long location, long length) {
        this.location = location;
        this.length = length;
      }
    }

    Pointer CFLocaleCopyCurrent();
    Pointer CFDateFormatterCreate(Pointer allocator, Pointer locale, long dateStyle, long timeStyle);
    Pointer CFDateFormatterGetFormat(Pointer formatter);
    long CFStringGetLength(Pointer str);
    void CFStringGetCharacters(Pointer str, CFRange range, char[] buffer);
    void CFRelease(Pointer p);
  }

  private static DateFormat[] getMacFormats() {
    CF cf = Native.load("CoreFoundation", CF.class);
    Pointer locale = cf.CFLocaleCopyCurrent();
    try {
      return new DateFormat[]{
        getMacFormat(cf, locale, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterNoStyle),  // short date
        getMacFormat(cf, locale, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterShortStyle),  // short time
        getMacFormat(cf, locale, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterMediumStyle),  // medium time
        getMacFormat(cf, locale, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterShortStyle)  // short date/time
      };
    }
    finally {
      cf.CFRelease(locale);
    }
  }

  private static DateFormat getMacFormat(CF cf, Pointer locale, long dateStyle, long timeStyle) {
    Pointer formatter = cf.CFDateFormatterCreate(null, locale, dateStyle, timeStyle);
    if (formatter == null) throw new IllegalStateException("CFDateFormatterCreate: null");
    try {
      Pointer format = cf.CFDateFormatterGetFormat(formatter);
      int length = (int)cf.CFStringGetLength(format);
      char[] buffer = new char[length];
      cf.CFStringGetCharacters(format, new CF.CFRange(0, length), buffer);
      return formatFromString(new String(buffer));
    }
    finally {
      cf.CFRelease(formatter);
    }
  }

  private static DateFormat[] getUnixFormats() {
    String localeStr = System.getenv("LC_TIME");
    if (LOG.isTraceEnabled()) LOG.trace("LC_TIME=" + localeStr);
    if (localeStr == null) return null;

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

    return new DateFormat[]{
      DateFormat.getDateInstance(DateFormat.SHORT, locale),
      DateFormat.getTimeInstance(DateFormat.SHORT, locale),
      DateFormat.getTimeInstance(DateFormat.MEDIUM, locale),
      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    };
  }

  @SuppressWarnings("SpellCheckingInspection")
  private interface Kernel32 extends StdCallLibrary {
    int LOCALE_SSHORTDATE  = 0x0000001F;
    int LOCALE_SSHORTTIME  = 0x00000079;
    int LOCALE_STIMEFORMAT = 0x00001003;

    int GetLocaleInfoEx(String localeName, int lcType, char[] lcData, int dataSize);
    int GetLastError();
  }

  private static DateFormat[] getWindowsFormats() {
    Kernel32 kernel32 = Native.load("Kernel32", Kernel32.class);
    int bufferSize = 128, rv;
    char[] buffer = new char[bufferSize];

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SSHORTDATE, buffer, bufferSize);
    if (rv < 2) throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    String shortDate = fixWindowsFormat(new String(buffer, 0, rv - 1));

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_SSHORTTIME, buffer, bufferSize);
    if (rv < 2) throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    String shortTime = fixWindowsFormat(new String(buffer, 0, rv - 1));

    rv = kernel32.GetLocaleInfoEx(null, Kernel32.LOCALE_STIMEFORMAT, buffer, bufferSize);
    if (rv < 2) throw new IllegalStateException("GetLocaleInfoEx: " + kernel32.GetLastError());
    String mediumTime = fixWindowsFormat(new String(buffer, 0, rv - 1));

    return new DateFormat[]{
      formatFromString(shortDate),
      formatFromString(shortTime),
      formatFromString(mediumTime),
      formatFromString(shortDate + " " + shortTime)
    };
  }

  private static String fixWindowsFormat(String format) {
    format = format.replaceAll("g+", "G");
    format = StringUtil.replace(format, "tt", "a");
    return format;
  }

  private static DateFormat formatFromString(String format) {
    try {
      return new SimpleDateFormat(format.trim());
    }
    catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("unrecognized format string '" + format + "'");
    }
  }
  //</editor-fold>
}