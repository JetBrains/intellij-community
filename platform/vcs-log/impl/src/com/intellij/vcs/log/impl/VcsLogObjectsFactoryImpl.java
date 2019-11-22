// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class VcsLogObjectsFactoryImpl implements VcsLogObjectsFactory {
  @NotNull
  private final VcsUserRegistry myUserRegistry;

  private VcsLogObjectsFactoryImpl(@NotNull Project project) {
    myUserRegistry = project.getService(VcsUserRegistry.class);
  }

  @NotNull
  @Override
  public Hash createHash(@NotNull String stringHash) {
    return HashImpl.build(stringHash);
  }

  @NotNull
  @Override
  public TimedVcsCommit createTimedCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
    return new TimedVcsCommitImpl(hash, parents, timeStamp);
  }

  @NotNull
  @Override
  public VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime,
                                                  @NotNull VirtualFile root, @NotNull String subject,
                                                  @NotNull String authorName, String authorEmail,
                                                  @NotNull String committerName, @NotNull String committerEmail, long authorTime) {
    VcsUser author = createUser(authorName, authorEmail);
    VcsUser committer = createUser(committerName, committerEmail);
    return new VcsShortCommitDetailsImpl(hash, parents, commitTime, root, subject, author, committer, authorTime);
  }

  @NotNull
  @Override
  public VcsCommitMetadata createCommitMetadata(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                                @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail,
                                                @NotNull String message, @NotNull String committerName,
                                                @NotNull String committerEmail, long authorTime) {
    VcsUser author = createUser(authorName, authorEmail);
    VcsUser committer = createUser(committerName, committerEmail);
    return new VcsCommitMetadataImpl(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
  }

  @NotNull
  @Override
  public VcsUser createUser(@NotNull String name, @NotNull String email) {
    return myUserRegistry.createUser(name, email);
  }

  @NotNull
  @Override
  public VcsRef createRef(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root) {
    return new VcsRefImpl(commitHash, name, type, root);
  }
}
