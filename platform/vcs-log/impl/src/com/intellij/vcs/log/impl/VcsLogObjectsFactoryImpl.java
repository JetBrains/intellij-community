// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class VcsLogObjectsFactoryImpl implements VcsLogObjectsFactory {
  private final @NotNull VcsUserRegistry myUserRegistry;

  private VcsLogObjectsFactoryImpl(@NotNull Project project) {
    myUserRegistry = project.getService(VcsUserRegistry.class);
  }

  @Override
  public @NotNull Hash createHash(@NotNull String stringHash) {
    return HashImpl.build(stringHash);
  }

  @Override
  public @NotNull TimedVcsCommit createTimedCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
    return new TimedVcsCommitImpl(hash, parents, timeStamp);
  }

  @Override
  public @NotNull VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime,
                                                           @NotNull VirtualFile root, @NotNull String subject,
                                                           @NotNull String authorName, String authorEmail,
                                                           @NotNull String committerName, @NotNull String committerEmail, long authorTime) {
    VcsUser author = createUser(authorName, authorEmail);
    VcsUser committer = createUser(committerName, committerEmail);
    return new VcsShortCommitDetailsImpl(hash, parents, commitTime, root, subject, author, committer, authorTime);
  }

  @Override
  public @NotNull VcsCommitMetadata createCommitMetadata(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                                         @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail,
                                                         @NotNull String message, @NotNull String committerName,
                                                         @NotNull String committerEmail, long authorTime) {
    VcsUser author = createUser(authorName, authorEmail);
    VcsUser committer = createUser(committerName, committerEmail);
    return new VcsCommitMetadataImpl(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
  }

  @Override
  public @NotNull VcsUser createUser(@NotNull String name, @NotNull String email) {
    return myUserRegistry.createUser(name, email);
  }

  @Override
  public @NotNull VcsRef createRef(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root) {
    return new VcsRefImpl(commitHash, name, type, root);
  }
}
