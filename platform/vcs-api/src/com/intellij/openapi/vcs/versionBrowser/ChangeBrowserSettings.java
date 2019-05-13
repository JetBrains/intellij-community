// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.text.SyncDateFormat;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.packNullables;
import static com.intellij.util.containers.ContainerUtil.retainAll;

public class ChangeBrowserSettings {
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

  @Nullable
  private static Date parseDate(@Nullable String dateValue) {
    try {
      return !isEmpty(dateValue) ? DATE_FORMAT.parse(dateValue) : null;
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }

  @Nullable
  private static Long parseLong(@Nullable String longValue) {
    try {
      return !isEmpty(longValue) ? Long.parseLong(longValue) : null;
    }
    catch (NumberFormatException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Nullable
  @Transient
  public Date getDateBefore() {
    return parseDate(DATE_BEFORE);
  }

  public void setDateBefore(@Nullable Date value) {
    DATE_BEFORE = value == null ? null : DATE_FORMAT.format(value);
  }

  @Nullable
  @Transient
  public Date getDateAfter() {
    return parseDate(DATE_AFTER);
  }

  public void setDateAfter(@Nullable Date value) {
    DATE_AFTER = value == null ? null : DATE_FORMAT.format(value);
  }

  @Nullable
  public Long getChangeBeforeFilter() {
    return USE_CHANGE_BEFORE_FILTER && !HEAD.equals(CHANGE_BEFORE) ? parseLong(CHANGE_BEFORE) : null;
  }

  @Nullable
  public Date getDateBeforeFilter() {
    return USE_DATE_BEFORE_FILTER ? parseDate(DATE_BEFORE) : null;
  }

  @Nullable
  public Long getChangeAfterFilter() {
    return USE_CHANGE_AFTER_FILTER ? parseLong(CHANGE_AFTER) : null;
  }

  @Nullable
  public Date getDateAfterFilter() {
    return USE_DATE_AFTER_FILTER ? parseDate(DATE_AFTER) : null;
  }

  @NotNull
  protected List<Filter> createFilters() {
    return packNullables(
      createDateFilter(getDateBeforeFilter(), true),
      createDateFilter(getDateAfterFilter(), false),
      createChangeFilter(getChangeBeforeFilter(), true),
      createChangeFilter(getChangeAfterFilter(), false),
      USE_USER_FILTER ? (Filter)changeList -> Comparing.equal(changeList.getCommitterName(), USER, false) : null
    );
  }

  @Nullable
  private static Filter createDateFilter(@Nullable Date date, boolean before) {
    return date == null ? null : changeList -> {
      Date commitDate = changeList.getCommitDate();

      return commitDate != null && (before ? commitDate.before(date) : commitDate.after(date));
    };
  }

  @Nullable
  private static Filter createChangeFilter(@Nullable Long number, boolean before) {
    return number == null ? null : changeList ->
      before ? changeList.getNumber() <= number : changeList.getNumber() >= number;
  }

  @NotNull
  public Filter createFilter() {
    List<Filter> filters = createFilters();
    return changeList -> filters.stream().allMatch(filter -> filter.accepts(changeList));
  }

  public void filterChanges(@NotNull List<? extends CommittedChangeList> changeLists) {
    Filter filter = createFilter();
    retainAll(changeLists, filter::accepts);
  }

  @Nullable
  @Transient
  public String getUserFilter() {
    return USE_USER_FILTER ? USER : null;
  }

  public boolean isAnyFilterSpecified() {
    return USE_CHANGE_AFTER_FILTER ||
           USE_CHANGE_BEFORE_FILTER ||
           USE_DATE_AFTER_FILTER ||
           USE_DATE_BEFORE_FILTER ||
           isNonDateFilterSpecified();
  }

  @Transient
  public boolean isNonDateFilterSpecified() {
    return USE_USER_FILTER;
  }
}
