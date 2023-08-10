// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsShortCommitDetailsImpl extends TimedVcsCommitImpl implements VcsShortCommitDetails {

  private final @NotNull String mySubject;
  private final @NotNull VcsUser myAuthor;
  private final @NotNull VirtualFile myRoot;
  private final @NotNull VcsUser myCommitter;
  private final long myAuthorTime;

  public VcsShortCommitDetailsImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                   @NotNull String subject, @NotNull VcsUser author, @NotNull VcsUser committer, long authorTime) {
    super(hash, parents, commitTime);
    myRoot = root;
    mySubject = subject;
    myAuthor = author;
    myCommitter = committer;
    myAuthorTime = authorTime;
  }

  @Override
  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  @Override
  public final @NotNull String getSubject() {
    return mySubject;
  }

  @Override
  public final @NotNull VcsUser getAuthor() {
    return myAuthor;
  }

  @Override
  public @NotNull VcsUser getCommitter() {
    return myCommitter;
  }

  @Override
  public long getAuthorTime() {
    return myAuthorTime;
  }

  @Override
  public long getCommitTime() {
    return getTimestamp();
  }

  @Override
  public String toString() {
    return getId().toShortString() + "(" + getSubject() + ")";
  }
}
