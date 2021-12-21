// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.util.text.JBDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

import static com.intellij.vcs.log.VcsLogFilterCollection.DATE_FILTER;

/**
 * Tells to filter by date. <br/>
 * Only before or after dates can be given, or both can be given.
 */
public interface VcsLogDateFilter extends VcsLogDetailsFilter {

  /**
   * If not null, only commits made after the returned date (inclusively) should be accepted.
   */
  @Nullable
  Date getAfter();

  /**
   * If not null, only commits made before the returned date (inclusively) should be accepted.
   */
  @Nullable
  Date getBefore();

  @NotNull
  @Override
  default VcsLogFilterCollection.FilterKey<VcsLogDateFilter> getKey() {
    return DATE_FILTER;
  }

  @NotNull
  @Override
  default String getDisplayText() {
    if (getBefore() != null && getAfter() != null) {
      String after = JBDateFormat.getFormatter().formatDate(getAfter());
      String before = JBDateFormat.getFormatter().formatDate(getBefore());
      return VcsLogBundle.message("vcs.log.filter.date.display.name.between", after, before);
    }
    else if (getAfter() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.display.name.after", JBDateFormat.getFormatter().formatDate(getAfter()));
    }
    else if (getBefore() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.display.name.before", JBDateFormat.getFormatter().formatDate(getBefore()));
    }
    return "";
  }

  @NotNull
  default String getDisplayTextWithPrefix() {
    if (getBefore() != null && getAfter() != null) {
      String after = JBDateFormat.getFormatter().formatDate(getAfter());
      String before = JBDateFormat.getFormatter().formatDate(getBefore());
      return VcsLogBundle.message("vcs.log.filter.date.presentation.with.prefix.made.between", after, before);
    }
    else if (getAfter() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.presentation.with.prefix.made.after", JBDateFormat.getFormatter().formatDate(getAfter()));
    }
    else if (getBefore() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.presentation.with.prefix.made.before", JBDateFormat.getFormatter().formatDate(getBefore()));
    }
    return "";
  }
}
