// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters;

import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDateFilter;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

/**
 * @see VcsLogFilterObject#fromDates
 */
class VcsLogDateFilterImpl implements VcsLogDateFilter, VcsLogDetailsFilter {
  private final @Nullable Date myAfter;
  private final @Nullable Date myBefore;

  VcsLogDateFilterImpl(@Nullable Date after, @Nullable Date before) {
    myAfter = after;
    myBefore = before;
  }

  @Override
  public boolean matches(@NotNull VcsCommitMetadata details) {
    Date date = new Date(details.getCommitTime());  // Git itself also filters by commit time, not author time
    boolean matches = true;
    if (myAfter != null) {
      matches &= date.after(myAfter);
    }
    if (myBefore != null) {
      matches &= date.before(myBefore);
    }
    return matches;
  }

  @Override
  public @Nullable Date getAfter() {
    return myAfter;
  }

  @Override
  public @Nullable Date getBefore() {
    return myBefore;
  }


  @Override
  public @NotNull String getDisplayText() {
    if (getBefore() != null && getAfter() != null) {
      String after = DateFormatUtil.formatDate(getAfter());
      String before = DateFormatUtil.formatDate(getBefore());
      return VcsLogBundle.message("vcs.log.filter.date.display.name.between", after, before);
    }
    else if (getAfter() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.display.name.after", DateFormatUtil.formatDate(getAfter()));
    }
    else if (getBefore() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.display.name.before", DateFormatUtil.formatDate(getBefore()));
    }
    return "";
  }

  static @Nls @NotNull String getDisplayTextWithPrefix(@NotNull VcsLogDateFilter filter) {
    if (filter.getBefore() != null && filter.getAfter() != null) {
      String after = DateFormatUtil.formatDate(filter.getAfter());
      String before = DateFormatUtil.formatDate(filter.getBefore());
      return VcsLogBundle.message("vcs.log.filter.date.presentation.with.prefix.made.between", after, before);
    }
    else if (filter.getAfter() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.presentation.with.prefix.made.after",
                                  DateFormatUtil.formatDate(filter.getAfter()));
    }
    else if (filter.getBefore() != null) {
      return VcsLogBundle.message("vcs.log.filter.date.presentation.with.prefix.made.before",
                                  DateFormatUtil.formatDate(filter.getBefore()));
    }
    return "";
  }

  @Override
  public String toString() {
    return (myAfter != null ? "after " + myAfter + (myBefore != null ? " " : "") : "") +
           (myBefore != null ? "before " + myBefore : "");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VcsLogDateFilterImpl filter = (VcsLogDateFilterImpl)o;
    return Objects.equals(getAfter(), filter.getAfter()) &&
           Objects.equals(getBefore(), filter.getBefore());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getAfter(), getBefore());
  }
}
