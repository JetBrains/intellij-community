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
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
* @author irengrig
*/
public class DateChangeListGroupingStrategy implements ChangeListGroupingStrategy {
  @NonNls private final SimpleDateFormat myWeekdayFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
  @NonNls private final SimpleDateFormat myMonthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
  @NonNls private final SimpleDateFormat myMonthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
  private long myTimeToRecalculateAfter;
  private Calendar myCurrentCalendar;
  private Calendar myCalendar;

  public String toString() {
    return VcsBundle.message("date.group.title");
  }

  public boolean changedSinceApply() {
    return System.currentTimeMillis() > myTimeToRecalculateAfter;
  }

  public void beforeStart() {
    myCurrentCalendar = Calendar.getInstance();
    myCalendar = Calendar.getInstance();
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
        return myWeekdayFormat.format(date);
      }
      if (myCurrentCalendar.get(Calendar.WEEK_OF_YEAR) == myCalendar.get(Calendar.WEEK_OF_YEAR)+1) {
        return VcsBundle.message("date.group.last.week");
      }
      return myMonthFormat.format(date);
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
}
