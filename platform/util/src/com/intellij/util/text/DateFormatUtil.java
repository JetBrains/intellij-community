// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.UtilBundle;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final SyncDateFormat ISO8601_FORMAT;

  static {
    SyncDateFormat[] formats = getDateTimeFormats();
    DATE_FORMAT = formats[0];
    TIME_FORMAT = formats[1];
    TIME_WITH_SECONDS_FORMAT = formats[2];
    DATE_TIME_FORMAT = formats[3];

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

  public static @NotNull SyncDateFormat getDateFormat() {
    return DATE_FORMAT;
  }

  public static @NotNull SyncDateFormat getTimeFormat() {
    return TIME_FORMAT;
  }

  public static @NotNull SyncDateFormat getTimeWithSecondsFormat() {
    return TIME_WITH_SECONDS_FORMAT;
  }

  public static @NotNull SyncDateFormat getDateTimeFormat() {
    return DATE_TIME_FORMAT;
  }

  public static @NotNull SyncDateFormat getIso8601Format() {
    return ISO8601_FORMAT;
  }

  public static @NlsSafe @NotNull String formatTime(@NotNull Date time) {
    return formatTime(time.getTime());
  }

  public static @NlsSafe @NotNull String formatTime(long time) {
    return getTimeFormat().format(time);
  }

  public static @NlsSafe @NotNull String formatTimeWithSeconds(@NotNull Date time) {
    return formatTimeWithSeconds(time.getTime());
  }

  public static @NlsSafe @NotNull String formatTimeWithSeconds(long time) {
    return getTimeWithSecondsFormat().format(time);
  }

  public static @NlsSafe @NotNull String formatDate(@NotNull Date time) {
    return formatDate(time.getTime());
  }

  public static @NlsSafe @NotNull String formatDate(long time) {
    return getDateFormat().format(time);
  }

  public static @NlsSafe @NotNull String formatPrettyDate(@NotNull Date date) {
    return formatPrettyDate(date.getTime());
  }

  public static @NlsSafe @NotNull String formatPrettyDate(long time) {
    String pretty = doFormatPretty(time, false);
    return pretty != null ? pretty : DATE_FORMAT.format(time);
  }

  public static @NlsSafe @NotNull String formatDateTime(Date date) {
    return formatDateTime(date.getTime());
  }

  public static @NlsSafe @NotNull String formatDateTime(long time) {
    return getDateTimeFormat().format(time);
  }

  public static @NlsSafe @NotNull String formatPrettyDateTime(@NotNull Date date) {
    return formatPrettyDateTime(date.getTime());
  }

  public static @NlsSafe @NotNull String formatPrettyDateTime(long time) {
    String pretty = doFormatPretty(time, true);
    return pretty != null ? pretty : DATE_TIME_FORMAT.format(time);
  }

  public static boolean isPrettyFormattingPossible(long time) {
    return doFormatPretty(time, true) != null;
  }

  private static @Nullable String doFormatPretty(long time, boolean formatTime) {
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
        return UtilBundle.message("date.format.minutes.ago", (int)Math.rint(delta / (double)MINUTE));
      }
    }

    boolean isToday = currentYear == year && currentDayOfYear == dayOfYear;
    if (isToday) {
      String result = UtilBundle.message("date.format.today");
      return formatTime ? result + " " + TIME_FORMAT.format(time) : result;
    }

    boolean isYesterdayOnPreviousYear =
      (currentYear == year + 1) && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(Calendar.DAY_OF_YEAR);
    boolean isYesterday = isYesterdayOnPreviousYear || (currentYear == year && currentDayOfYear == dayOfYear + 1);
    if (isYesterday) {
      String result = UtilBundle.message("date.format.yesterday");
      return formatTime ? result + " " + TIME_FORMAT.format(time) : result;
    }

    return null;
  }

  public static @NlsSafe @NotNull String formatFrequency(long time) {
    return UtilBundle.message("date.frequency", formatBetweenDates(time, 0));
  }

  public static @NlsSafe @NotNull String formatBetweenDates(long d1, long d2) {
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

  /** @deprecated use {@link com.intellij.ide.nls.NlsMessages#formatDateLong} */
  @Deprecated
  public static @NlsSafe @NotNull String formatAboutDialogDate(@NotNull Date date) {
    return formatAboutDialogDate(date.getTime());
  }

  /** @deprecated use {@link com.intellij.ide.nls.NlsMessages#formatDateLong} */
  @Deprecated
  public static @NlsSafe @NotNull String formatAboutDialogDate(long time) {
    return DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(time);
  }

  /**
   * Return sample date that can be used to determine preferred string width.
   * <p>
   * We should not use {@code new Date()} to ensure results are reproducible (and to avoid "Today" for pretty formats).
   * Returned date is expected to return maximum width string for date formats like "d.m.yy H:M".
   */
  public static @NotNull Date getSampleDateTime() {
    @SuppressWarnings("deprecation") Date date = new Date(100, Calendar.DECEMBER, 31, 23, 59);
    return date;
  }

  //<editor-fold desc="Helpers.">
  private static String someTimeAgoMessage(Period period, int n) {
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

  private static String composeInSomeTimeMessage(Period period, int n) {
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
      else if (SystemInfo.isWindows && JnaLoader.isLoaded() ) {
        formats = getWindowsFormats();
      }
    }
    catch (Throwable t) {
      LOG.error(t);
    }
    if (formats == null) {
      LOG.info("cannot load system formats (JNA=" + JnaLoader.isLoaded() + "), resorting to JRE for " + Locale.getDefault(Locale.Category.FORMAT));
      formats = new DateFormat[]{
        DateFormat.getDateInstance(DateFormat.SHORT),
        DateFormat.getTimeInstance(DateFormat.SHORT),
        DateFormat.getTimeInstance(DateFormat.MEDIUM),
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
      };
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
    Pointer CFLocaleGetIdentifier(Pointer locale);
    Pointer CFDateFormatterCreate(Pointer allocator, Pointer locale, long dateStyle, long timeStyle);
    Pointer CFDateFormatterGetFormat(Pointer formatter);
    long CFStringGetLength(Pointer str);
    void CFStringGetCharacters(Pointer str, CFRange range, char[] buffer);
    void CFRelease(Pointer p);
  }

  private static DateFormat[] getMacFormats() {
    CF cf = Native.load("CoreFoundation", CF.class);
    Pointer localeRef = cf.CFLocaleCopyCurrent();
    try {
      String localeId = getMacString(cf, cf.CFLocaleGetIdentifier(localeRef));
      if (LOG.isTraceEnabled()) LOG.trace("id=" + localeId);
      Locale locale = getLocaleById(localeId);
      return new DateFormat[]{
        getMacFormat(cf, localeRef, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterNoStyle, locale),  // short date
        getMacFormat(cf, localeRef, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterShortStyle, locale),  // short time
        getMacFormat(cf, localeRef, CF.kCFDateFormatterNoStyle, CF.kCFDateFormatterMediumStyle, locale),  // medium time
        getMacFormat(cf, localeRef, CF.kCFDateFormatterShortStyle, CF.kCFDateFormatterShortStyle, locale)  // short date/time
      };
    }
    finally {
      cf.CFRelease(localeRef);
    }
  }

  private static DateFormat getMacFormat(CF cf, Pointer localeRef, long dateStyle, long timeStyle, Locale locale) {
    Pointer formatter = cf.CFDateFormatterCreate(null, localeRef, dateStyle, timeStyle);
    if (formatter == null) throw new IllegalStateException("CFDateFormatterCreate: null");
    try {
      Pointer format = cf.CFDateFormatterGetFormat(formatter);
      return formatFromString(getMacString(cf, format), locale);
    }
    finally {
      cf.CFRelease(formatter);
    }
  }

  private static String getMacString(CF cf, Pointer ref) {
    int length = (int)cf.CFStringGetLength(ref);
    char[] buffer = new char[length];
    cf.CFStringGetCharacters(ref, new CF.CFRange(0, length), buffer);
    return new String(buffer);
  }

  private static DateFormat @Nullable [] getUnixFormats() {
    String localeStr = System.getenv("LC_TIME");
    if (LOG.isTraceEnabled()) LOG.trace("LC_TIME=" + localeStr);
    if (localeStr == null) return null;

    Locale locale = getLocaleById(localeStr.trim());
    return new DateFormat[]{
      DateFormat.getDateInstance(DateFormat.SHORT, locale),
      DateFormat.getTimeInstance(DateFormat.SHORT, locale),
      DateFormat.getTimeInstance(DateFormat.MEDIUM, locale),
      DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
    };
  }

  private static Locale getLocaleById(String localeStr) {
    int p = localeStr.indexOf('.');
    if (p > 0) localeStr = localeStr.substring(0, p);
    p = localeStr.indexOf('@');
    if (p > 0) localeStr = localeStr.substring(0, p);
    p = localeStr.indexOf('_');
    if (p < 0) {
      return new Locale(localeStr);
    }
    else {
      return new Locale(localeStr.substring(0, p), localeStr.substring(p + 1));
    }
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

    Locale locale = Locale.getDefault(Locale.Category.FORMAT);
    return new DateFormat[]{
      formatFromString(shortDate, locale),
      formatFromString(shortTime, locale),
      formatFromString(mediumTime, locale),
      formatFromString(shortDate + ' ' + shortTime, locale)
    };
  }

  private static String fixWindowsFormat(String format) {
    return format.replaceAll("g+", "G").replace("tt", "a");
  }

  private static DateFormat formatFromString(String format, Locale locale) {
    try {
      if (LOG.isTraceEnabled()) LOG.trace("'" + format + "' in " + locale);
      return new SimpleDateFormat(format.trim(), locale);
    }
    catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("unrecognized format string '" + format + "'");
    }
  }
  //</editor-fold>
}
