// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
* @author irengrig
*/
public final class DateChangeListGroupingStrategy implements ChangeListGroupingStrategy {
  @NonNls private final SimpleDateFormat myMonthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
  private long myTimeToRecalculateAfter;
  private Calendar myCurrentCalendar;
  private final Calendar myCalendar;
  private final WeekDayFormatCache myWeekDayFormatCache;
  private final MonthsCache myMonthsCache;

  public String toString() {
    return VcsBundle.message("date.group.title");
  }

  @Override
  public boolean changedSinceApply() {
    return System.currentTimeMillis() > myTimeToRecalculateAfter;
  }

  public DateChangeListGroupingStrategy() {
    myCalendar = Calendar.getInstance();
    myWeekDayFormatCache = new WeekDayFormatCache(myCalendar);
    myMonthsCache = new MonthsCache(myCalendar);
  }

  @Override
  public void beforeStart() {
    myCurrentCalendar = Calendar.getInstance();
    myCurrentCalendar.setTimeInMillis(0);
    // +- seconds etc
    myCurrentCalendar.set(Calendar.HOUR, 0);
    myCurrentCalendar.set(Calendar.MINUTE, 0);

    myTimeToRecalculateAfter = myCurrentCalendar.getTimeInMillis() + 23 * 60 * 60 * 1000;
    myCurrentCalendar.setTime(new Date());
  }

  @Override
  public String getGroupName(CommittedChangeList changeList) {
    return getGroupName(changeList.getCommitDate());
  }

  @Nls
  public String getGroupName(final Date date) {
    myCalendar.setTime(date);
    if (myCurrentCalendar.get(Calendar.YEAR) == myCalendar.get(Calendar.YEAR)) {
      if (myCurrentCalendar.get(Calendar.DAY_OF_YEAR) == myCalendar.get(Calendar.DAY_OF_YEAR)) {
        return VcsBundle.message("date.group.today");
      }
      if (myCurrentCalendar.get(Calendar.WEEK_OF_YEAR) == myCalendar.get(Calendar.WEEK_OF_YEAR)) {
        return myWeekDayFormatCache.get(myCalendar.get(Calendar.DAY_OF_WEEK));
      }
      if (myCurrentCalendar.get(Calendar.WEEK_OF_YEAR) == myCalendar.get(Calendar.WEEK_OF_YEAR)+1) {
        return VcsBundle.message("date.group.last.week");
      }
      return myMonthsCache.get(myCalendar.get(Calendar.MONTH));
    }
    return myMonthYearFormat.format(date);
  }

  @Override
  public Comparator<CommittedChangeList> getComparator() {
    return CommittedChangeListByDateComparator.DESCENDING;
  }

  private static final class MonthsCache {
    private final Int2ObjectMap<@Nls String> myCache = new Int2ObjectOpenHashMap<>(12);

    private MonthsCache(Calendar calendarForInit) {
      SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
      for (int i = 0; i < 12; i++) {
        calendarForInit.set(Calendar.MONTH, i);
        myCache.put(i, monthFormat.format(calendarForInit.getTime()));
      }
    }

    @Nls
    public String get(int month) {
      return myCache.get(month);
    }
  }

  private static final class WeekDayFormatCache {
    private final Int2ObjectMap<@Nls String> myCache = new Int2ObjectOpenHashMap<>(7);

    private WeekDayFormatCache(final Calendar calendarForInit) {
      SimpleDateFormat weekdayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
      for (int i = 1; i < 8; i++) {
        calendarForInit.set(Calendar.DAY_OF_WEEK, i);
        myCache.put(i, weekdayFormat.format(calendarForInit.getTime()));
      }
    }

    @Nls
    public String get(int dayOfWeek) {
      return myCache.get(dayOfWeek);
    }
  }
}
