/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.util.text.SyncDateFormat;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class ChangeBrowserSettings implements JDOMExternalizable {

  public interface Filter {
    boolean accepts(CommittedChangeList change);
  }

  public static final String HEAD = "HEAD";
  public static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG));

  private static final Logger LOG = Logger.getInstance(ChangeBrowserSettings.class);

  public boolean USE_DATE_BEFORE_FILTER = false;
  public boolean USE_DATE_AFTER_FILTER = false;
  public boolean USE_CHANGE_BEFORE_FILTER = false;
  public boolean USE_CHANGE_AFTER_FILTER = false;

  public String DATE_BEFORE = "";
  public String DATE_AFTER = "";

  public String CHANGE_BEFORE = "";
  public String CHANGE_AFTER = "";

  public boolean USE_USER_FILTER = false;
  public String USER = "";
  public boolean STOP_ON_COPY = false;

  @Transient public boolean STRICTLY_AFTER = false;

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Nullable
  private static Date parseDate(@Nullable String dateStr) {
    if (dateStr == null || dateStr.isEmpty()) return null;
    try {
      return DATE_FORMAT.parse(dateStr);
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }

  public void setDateBefore(@Nullable Date value) {
    DATE_BEFORE = value == null ? null : DATE_FORMAT.format(value);
  }

  @Nullable
  public Date getDateBefore() {
    return parseDate(DATE_BEFORE);
  }

  @Nullable
  public Date getDateAfter() {
    return parseDate(DATE_AFTER);
  }

  @Nullable
  public Long getChangeBeforeFilter() {
    if (USE_CHANGE_BEFORE_FILTER && CHANGE_BEFORE.length() > 0) {
      if (HEAD.equals(CHANGE_BEFORE)) return null;
      return Long.parseLong(CHANGE_BEFORE);      
    }
    return null;
  }

  @Nullable
  public Date getDateBeforeFilter() {
    return USE_DATE_BEFORE_FILTER ? parseDate(DATE_BEFORE) : null;
  }

  @Nullable
  public Long getChangeAfterFilter() {
    if (USE_CHANGE_AFTER_FILTER && CHANGE_AFTER.length() > 0) {
      return Long.parseLong(CHANGE_AFTER);
    }
    return null;
  }

  @Nullable
  public Date getDateAfterFilter() {
    return USE_DATE_AFTER_FILTER ? parseDate(DATE_AFTER) : null;
  }

  public void setDateAfter(@Nullable Date value) {
    DATE_AFTER = value == null ? null : DATE_FORMAT.format(value);
  }

  @NotNull
  protected List<Filter> createFilters() {
    final ArrayList<Filter> result = new ArrayList<>();
    addDateFilter(USE_DATE_BEFORE_FILTER, getDateBefore(), result, true);
    addDateFilter(USE_DATE_AFTER_FILTER, getDateAfter(), result, false);

    if (USE_CHANGE_BEFORE_FILTER) {
      try {
        final long numBefore = Long.parseLong(CHANGE_BEFORE);
        result.add(new Filter() {
          public boolean accepts(CommittedChangeList change) {
            return change.getNumber() <= numBefore;
          }
        });
      }
      catch (NumberFormatException e) {
        //ignore
        LOG.info(e);
      }
    }

    if (USE_CHANGE_AFTER_FILTER) {
      try {
        final long numAfter = Long.parseLong(CHANGE_AFTER);
        result.add(new Filter() {
          public boolean accepts(CommittedChangeList change) {
            return change.getNumber() >= numAfter;
          }
        });
      }
      catch (NumberFormatException e) {
        //ignore
        LOG.info(e);
      }
    }

    if (USE_USER_FILTER) {
      result.add(new Filter() {
        public boolean accepts(CommittedChangeList change) {
          return Comparing.equal(change.getCommitterName(), USER, false);
        }
      });
    }

    return result;
  }

  private static void addDateFilter(final boolean useFilter, final Date date, final ArrayList<Filter> result, final boolean before) {
    if (useFilter) {
      assert date != null;
      result.add(new Filter() {
        public boolean accepts(CommittedChangeList change) {
          final Date changeDate = change.getCommitDate();
          if (changeDate == null) return false;

          return before ? changeDate.before(date) : changeDate.after(date);
        }
      });
    }
  }

  @NotNull
  public Filter createFilter() {
    final List<Filter> filters = createFilters();
    return new Filter() {
      public boolean accepts(CommittedChangeList change) {
        for (Filter filter : filters) {
          if (!filter.accepts(change)) return false;
        }
        return true;
      }
    };
  }

  public void filterChanges(@NotNull List<? extends CommittedChangeList> changeListInfos) {
    Filter filter = createFilter();
    for (Iterator<? extends CommittedChangeList> iterator = changeListInfos.iterator(); iterator.hasNext();) {
      CommittedChangeList changeListInfo = iterator.next();
      if (!filter.accepts(changeListInfo)) {
        iterator.remove();
      }
    }
  }

  @Nullable
  public String getUserFilter() {
    return USE_USER_FILTER ? USER : null;
  }

  public boolean isAnyFilterSpecified() {
    return USE_CHANGE_AFTER_FILTER || USE_CHANGE_BEFORE_FILTER || USE_DATE_AFTER_FILTER || USE_DATE_BEFORE_FILTER ||
           isNonDateFilterSpecified();
  }

  public boolean isNonDateFilterSpecified() {
    return USE_USER_FILTER;
  }
}
