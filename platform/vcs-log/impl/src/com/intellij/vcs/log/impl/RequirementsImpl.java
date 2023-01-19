// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsLogProviderRequirementsEx;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RequirementsImpl implements VcsLogProviderRequirementsEx {

  private final int myCommitCount;
  private final boolean myRefresh;
  private final @NotNull Collection<VcsRef> myPreviousRefs;

  public RequirementsImpl(int count, boolean refresh, @NotNull Collection<VcsRef> previousRefs) {
    myCommitCount = count;
    myRefresh = refresh;
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
  public @NotNull Collection<VcsRef> getPreviousRefs() {
    return myPreviousRefs;
  }
}
