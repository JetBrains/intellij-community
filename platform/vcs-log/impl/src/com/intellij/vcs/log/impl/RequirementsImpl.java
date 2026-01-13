// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsLogProviderRequirementsEx;
import com.intellij.vcs.log.VcsLogRefsOfSingleRoot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class RequirementsImpl implements VcsLogProviderRequirementsEx {

  private final int myCommitCount;
  private final boolean myRefresh;
  private final @NotNull VcsLogRefsOfSingleRoot myPreviousRefs;
  private final boolean myIsRefreshRefs;

  public RequirementsImpl(int count, boolean refresh, @NotNull VcsLogRefsOfSingleRoot previousRefs) {
    this(count, refresh, previousRefs, true);
  }

  public RequirementsImpl(int count, boolean refresh, @NotNull VcsLogRefsOfSingleRoot previousRefs, boolean isRefreshRefs) {
    myCommitCount = count;
    myRefresh = refresh;
    myIsRefreshRefs = isRefreshRefs;
    myPreviousRefs = previousRefs;
  }

  @Override
  public int getCommitCount() {
    return myCommitCount;
  }

  @Override
  public boolean isRefresh() {
    return myRefresh;
  }

  @Override
  public boolean isRefreshRefs() {
    return myIsRefreshRefs;
  }

  @Override
  public @NotNull VcsLogRefsOfSingleRoot getPreviousRefs() {
    return myPreviousRefs;
  }

  @Override
  public String toString() {
    return "RequirementsImpl{" +
           "myCommitCount=" + myCommitCount +
           ", myRefresh=" + myRefresh +
           ", myPreviousRefs=" + myPreviousRefs +
           ", myIsRefreshRefs=" + myIsRefreshRefs +
           '}';
  }
}
