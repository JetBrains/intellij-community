// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LogDataImpl implements VcsLogProvider.DetailedLogData, VcsLogProvider.LogData {

  private static final LogDataImpl EMPTY = new LogDataImpl(Collections.emptySet(),
                                                           Collections.emptySet(),
                                                           Collections.emptyList());

  private final @NotNull List<VcsCommitMetadata> myCommits;
  private final @NotNull Set<VcsRef> myRefs;
  private final @NotNull Set<VcsUser> myUsers;

  public static @NotNull LogDataImpl empty() {
    return EMPTY;
  }

  public LogDataImpl(@NotNull Set<VcsRef> refs, @NotNull Set<VcsUser> users) {
    this(refs, users, Collections.emptyList());
  }

  public LogDataImpl(@NotNull Set<VcsRef> refs, @NotNull List<VcsCommitMetadata> metadatas) {
    this(refs, Collections.emptySet(), metadatas);
  }

  private LogDataImpl(@NotNull Set<VcsRef> refs, @NotNull Set<VcsUser> users, @NotNull List<VcsCommitMetadata> commits) {
    myRefs = refs;
    myUsers = users;
    myCommits = commits;
  }

  @Override
  public @NotNull List<VcsCommitMetadata> getCommits() {
    return myCommits;
  }

  @Override
  public @NotNull Set<VcsRef> getRefs() {
    return myRefs;
  }

  @Override
  public @NotNull Set<VcsUser> getUsers() {
    return myUsers;
  }
}
