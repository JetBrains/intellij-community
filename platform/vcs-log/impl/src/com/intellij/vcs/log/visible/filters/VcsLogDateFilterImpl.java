// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters;

import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogDateFilter;
import com.intellij.vcs.log.VcsLogDetailsFilter;
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
