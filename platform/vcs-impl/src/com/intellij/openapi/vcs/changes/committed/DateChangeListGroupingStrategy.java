/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NonNls;

import java.text.SimpleDateFormat;
import java.util.*;

/**
* @author irengrig
*/
public class DateChangeListGroupingStrategy implements ChangeListGroupingStrategy {
  @NonNls private final SimpleDateFormat myMonthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
  private long myTimeToRecalculateAfter;
  private Calendar myCurrentCalendar;
  private Calendar myCalendar;
  private final WeekDayFormatCache myWeekDayFormatCache;
  private final MonthsCache myMonthsCache;

  public String toString() {
    return VcsBundle.message("date.group.title");
  }

  public boolean changedSinceApply() {
    return System.currentTimeMillis() > myTimeToRecalculateAfter;
  }

  public DateChangeListGroupingStrategy() {
    myCalendar = Calendar.getInstance();
    myWeekDayFormatCache = new WeekDayFormatCache(myCalendar);
    myMonthsCache = new MonthsCache(myCalendar);
  }

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

  public Comparator<CommittedChangeList> getComparator() {
    return new Comparator<CommittedChangeList>() {
      public int compare(final CommittedChangeList o1, final CommittedChangeList o2) {
        return -o1.getCommitDate().compareTo(o2.getCommitDate());
      }
    };
  }

  private static class MonthsCache {
    @NonNls private final SimpleDateFormat myMonthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
    private final Map<Integer, String> myCache;

    private MonthsCache(final Calendar calendarForInit) {
      myCache = new HashMap<Integer, String>();
      for (int i = 0; i < 12; i++) {
        calendarForInit.set(Calendar.MONTH, i);
        myCache.put(i, myMonthFormat.format(calendarForInit.getTime()));
      }
    }

    public String get(final int month) {
      return myCache.get(month);
    }
  }

  private static class WeekDayFormatCache {
    @NonNls private final SimpleDateFormat myWeekdayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
    private final Map<Integer, String> myCache;

    private WeekDayFormatCache(final Calendar calendarForInit) {
      myCache = new HashMap<Integer, String>();
      for (int i = 1; i < 8; i++) {
        calendarForInit.set(Calendar.DAY_OF_WEEK, i);
        myCache.put(i, myWeekdayFormat.format(calendarForInit.getTime()));
      }
    }

    public String get(final int dayOfWeek) {
      return myCache.get(dayOfWeek);
    }
  }
}
