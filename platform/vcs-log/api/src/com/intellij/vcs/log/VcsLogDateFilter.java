// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

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

  @Override
  default @NotNull VcsLogFilterCollection.FilterKey<VcsLogDateFilter> getKey() {
    return DATE_FILTER;
  }
}
