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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Allows to postpone changes parsing, which might take long for a large amount of commits,
 * because {@link Change} holds {@link LocalFilePath} which makes costly refreshes and type detections.
 */
public class VcsChangesLazilyParsedDetails extends VcsCommitMetadataImpl implements VcsFullCommitDetails {

  private static final Logger LOG = Logger.getInstance(VcsChangesLazilyParsedDetails.class);

  @NotNull protected final ThrowableComputable<Collection<Change>, ? extends Exception> myChangesGetter;

  public VcsChangesLazilyParsedDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                       @NotNull String subject, @NotNull VcsUser author, @NotNull String message,
                                       @NotNull VcsUser committer, long authorTime,
                                       @NotNull ThrowableComputable<Collection<Change>, ? extends Exception> changesGetter) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
    myChangesGetter = changesGetter;
  }

  @NotNull
  @Override
  public Collection<Change> getChanges() {
    try {
      return myChangesGetter.compute();
    }
    catch (Exception e) {
      LOG.error("Error happened when parsing changes", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  public Collection<String> getModifiedPaths() {
    Set<String> changedPaths = ContainerUtil.newHashSet();
    for (Change change : getChanges()) {
      if (change.getAfterRevision() != null) changedPaths.add(change.getAfterRevision().getFile().getPath());
      if (change.getBeforeRevision() != null) changedPaths.add(change.getBeforeRevision().getFile().getPath());
    }
    return changedPaths;
  }

  @NotNull
  public Collection<Couple<String>> getRenamedPaths() {
    Set<Couple<String>> renames = ContainerUtil.newHashSet();
    for (Change change : getChanges()) {
      if (change.getType().equals(Change.Type.MOVED)) {
        if (change.getAfterRevision() != null && change.getBeforeRevision() != null) {
          renames.add(Couple.of(change.getBeforeRevision().getFile().getPath(), change.getAfterRevision().getFile().getPath()));
        }
      }
    }
    return renames;
  }
}
