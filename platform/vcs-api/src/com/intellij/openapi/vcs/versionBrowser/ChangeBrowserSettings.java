// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.List;

public class ChangeBrowserSettings {
  public interface Filter {
    boolean accepts(CommittedChangeList change);
  }

  public static final String HEAD = "HEAD";

  @VisibleForTesting
  public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG);

  private static final Logger LOG = Logger.getInstance(ChangeBrowserSettings.class);

  public boolean USE_DATE_BEFORE_FILTER = false;
  public boolean USE_DATE_AFTER_FILTER = false;
  public boolean USE_CHANGE_BEFORE_FILTER = false;
  public boolean USE_CHANGE_AFTER_FILTER = false;

  public @Nullable String DATE_BEFORE = "";
  public @Nullable String DATE_AFTER = "";

  public String CHANGE_BEFORE = "";
  public String CHANGE_AFTER = "";

  public boolean USE_USER_FILTER = false;
  public @NlsSafe String USER = "";
  public boolean STOP_ON_COPY = false;

  @Transient public boolean STRICTLY_AFTER = false;

  private static @Nullable Date parseDate(@Nullable String dateValue) {
    try {
      return !Strings.isEmpty(dateValue) ? Date.from(Instant.from(DATE_FORMAT.parse(dateValue))) : null;
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }

  private static @Nullable Long parseLong(@Nullable String longValue) {
    try {
      return !Strings.isEmpty(longValue) ? Long.parseLong(longValue) : null;
    }
    catch (NumberFormatException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Transient
  public @Nullable Date getDateBefore() {
    return parseDate(DATE_BEFORE);
  }

  public void setDateBefore(@Nullable Date value) {
    DATE_BEFORE = value == null ? null : DATE_FORMAT.format(value.toInstant().atZone(ZoneId.systemDefault()));
  }

  @Transient
  public @Nullable Date getDateAfter() {
    return parseDate(DATE_AFTER);
  }

  public void setDateAfter(@Nullable Date value) {
    DATE_AFTER = value == null ? null : DATE_FORMAT.format(value.toInstant().atZone(ZoneId.systemDefault()));
  }

  public @Nullable Long getChangeBeforeFilter() {
    return USE_CHANGE_BEFORE_FILTER && !HEAD.equals(CHANGE_BEFORE) ? parseLong(CHANGE_BEFORE) : null;
  }

  public @Nullable Date getDateBeforeFilter() {
    return USE_DATE_BEFORE_FILTER ? parseDate(DATE_BEFORE) : null;
  }

  public @Nullable Long getChangeAfterFilter() {
    return USE_CHANGE_AFTER_FILTER ? parseLong(CHANGE_AFTER) : null;
  }

  public @Nullable Date getDateAfterFilter() {
    return USE_DATE_AFTER_FILTER ? parseDate(DATE_AFTER) : null;
  }

  // used externally
  protected @Unmodifiable @NotNull List<Filter> createFilters() {
    return ContainerUtil.packNullables(
      createDateFilter(getDateBeforeFilter(), true),
      createDateFilter(getDateAfterFilter(), false),
      createChangeFilter(getChangeBeforeFilter(), true),
      createChangeFilter(getChangeAfterFilter(), false),
      USE_USER_FILTER ? changeList -> Comparing.equal(changeList.getCommitterName(), USER, false) : null
    );
  }

  private static @Nullable Filter createDateFilter(@Nullable Date date, boolean before) {
    return date == null ? null : changeList -> {
      Date commitDate = changeList.getCommitDate();
      return commitDate != null && (before ? commitDate.before(date) : commitDate.after(date));
    };
  }

  private static @Nullable Filter createChangeFilter(@Nullable Long number, boolean before) {
    return number == null ? null : changeList -> {
      return before ? changeList.getNumber() <= number : changeList.getNumber() >= number;
    };
  }

  public @NotNull Filter createFilter() {
    List<Filter> filters = createFilters();
    return changeList -> ContainerUtil.and(filters, filter -> filter.accepts(changeList));
  }

  public void filterChanges(@NotNull List<? extends CommittedChangeList> changeLists) {
    Filter filter = createFilter();
    ContainerUtil.retainAll(changeLists, filter::accepts);
  }

  @Transient
  public @Nullable String getUserFilter() {
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
