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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsCommitMetadataImpl extends VcsShortCommitDetailsImpl implements VcsCommitMetadata {

  @NotNull private final String myFullMessage;

  public VcsCommitMetadataImpl(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                               @NotNull String subject, @NotNull VcsUser author, @NotNull String message,
                               @NotNull VcsUser committer, long authorTime) {
    super(hash, parents, commitTime, root, subject, author, committer, authorTime);
    myFullMessage = message;
  }

  @Override
  @NotNull
  public String getFullMessage() {
    return myFullMessage;
  }
}
