// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsCommitMetadataImpl extends VcsShortCommitDetailsImpl implements VcsCommitMetadata {

  private final @NotNull String myFullMessage;

  public VcsCommitMetadataImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                               @NotNull String subject, @NotNull VcsUser author, @NotNull String message,
                               @NotNull VcsUser committer, long authorTime) {
    super(hash, parents, commitTime, root, subject, author, committer, authorTime);
    myFullMessage = message.equals(getSubject()) ? getSubject() : message;
  }

  @Override
  public @NotNull String getFullMessage() {
    return myFullMessage;
  }
}
