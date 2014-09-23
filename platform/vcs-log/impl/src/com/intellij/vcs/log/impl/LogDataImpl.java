/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  private static final LogDataImpl EMPTY = new LogDataImpl(Collections.<VcsRef>emptySet(),
                                                           Collections.<VcsUser>emptySet(),
                                                           Collections.<VcsCommitMetadata>emptyList());

  @NotNull private final List<VcsCommitMetadata> myCommits;
  @NotNull private final Set<VcsRef> myRefs;
  @NotNull private final Set<VcsUser> myUsers;

  @NotNull
  public static LogDataImpl empty() {
    return EMPTY;
  }

  public LogDataImpl(@NotNull Set<VcsRef> refs, @NotNull Set<VcsUser> users) {
    this(refs, users, Collections.<VcsCommitMetadata>emptyList());
  }

  public LogDataImpl(@NotNull Set<VcsRef> refs, @NotNull List<VcsCommitMetadata> metadatas) {
    this(refs, Collections.<VcsUser>emptySet(), metadatas);
  }

  private LogDataImpl(@NotNull Set<VcsRef> refs, @NotNull Set<VcsUser> users, @NotNull List<VcsCommitMetadata> commits) {
    myRefs = refs;
    myUsers = users;
    myCommits = commits;
  }

  @NotNull
  @Override
  public List<VcsCommitMetadata> getCommits() {
    return myCommits;
  }

  @Override
  @NotNull
  public Set<VcsRef> getRefs() {
    return myRefs;
  }

  @NotNull
  @Override
  public Set<VcsUser> getUsers() {
    return myUsers;
  }
}
