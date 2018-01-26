// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogRevisionFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class VcsLogRevisionFilterImpl implements VcsLogRevisionFilter {
  @NotNull private final Collection<CommitId> myHeads;

  public VcsLogRevisionFilterImpl(@NotNull Collection<CommitId> heads) {myHeads = heads;}

  @NotNull
  public static VcsLogRevisionFilterImpl fromCommit(@NotNull CommitId commit) {
    return new VcsLogRevisionFilterImpl(Collections.singletonList(commit));
  }

  @NotNull
  @Override
  public Collection<CommitId> getHeads() {
    return myHeads;
  }
}
