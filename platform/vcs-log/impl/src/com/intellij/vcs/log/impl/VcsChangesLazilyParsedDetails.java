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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsStatusDescriptor.MergedStatusInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allows to postpone changes parsing, which might take long for a large amount of commits,
 * because {@link Change} holds {@link LocalFilePath} which makes costly refreshes and type detections.
 */
public abstract class VcsChangesLazilyParsedDetails extends VcsCommitMetadataImpl implements VcsFullCommitDetails, VcsIndexableDetails {
  private static final Logger LOG = Logger.getInstance(VcsChangesLazilyParsedDetails.class);
  protected static final Changes EMPTY_CHANGES = new EmptyChanges();
  @NotNull protected final AtomicReference<Changes> myChanges = new AtomicReference<>();

  public VcsChangesLazilyParsedDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                       @NotNull String subject, @NotNull VcsUser author, @NotNull String message,
                                       @NotNull VcsUser committer, long authorTime) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
  }

  @NotNull
  @Override
  public Collection<String> getModifiedPaths(int parent) {
    return myChanges.get().getModifiedPaths(parent);
  }

  @NotNull
  @Override
  public Collection<Couple<String>> getRenamedPaths(int parent) {
    return myChanges.get().getRenamedPaths(parent);
  }

  @NotNull
  @Override
  public Collection<Change> getChanges() {
    try {
      return myChanges.get().getMergedChanges();
    }
    catch (VcsException e) {
      LOG.error("Error happened when parsing changes", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  @Override
  public Collection<Change> getChanges(int parent) {
    try {
      return myChanges.get().getChanges(parent);
    }
    catch (VcsException e) {
      LOG.error("Error happened when parsing changes", e);
      return Collections.emptyList();
    }
  }

  @Override
  public boolean hasRenames() {
    return true;
  }

  public interface Changes {
    @NotNull
    Collection<Change> getMergedChanges() throws VcsException;

    @NotNull
    Collection<Change> getChanges(int parent) throws VcsException;

    @NotNull
    Collection<String> getModifiedPaths(int parent);

    @NotNull
    Collection<Couple<String>> getRenamedPaths(int parent);
  }

  protected static class EmptyChanges implements Changes {
    @NotNull
    @Override
    public Collection<Change> getMergedChanges() {
      return ContainerUtil.emptyList();
    }

    @NotNull
    @Override
    public Collection<Change> getChanges(int parent) {
      return ContainerUtil.emptyList();
    }

    @NotNull
    @Override
    public Collection<String> getModifiedPaths(int parent) {
      return ContainerUtil.emptyList();
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      return ContainerUtil.emptyList();
    }
  }

  protected abstract class UnparsedChanges<S> implements Changes {
    @NotNull protected final Project myProject;
    @NotNull protected final List<List<S>> myChangesOutput;
    @NotNull private final VcsStatusDescriptor<S> myDescriptor;

    public UnparsedChanges(@NotNull Project project,
                           @NotNull List<List<S>> changesOutput,
                           @NotNull VcsStatusDescriptor<S> descriptor) {
      myProject = project;
      myChangesOutput = changesOutput;
      myDescriptor = descriptor;
    }

    @NotNull
    protected ParsedChanges parseChanges() throws VcsException {
      List<Change> mergedChanges = parseMergedChanges();
      List<Collection<Change>> changes = computeChanges(mergedChanges);
      ParsedChanges parsedChanges = new ParsedChanges(mergedChanges, changes);
      myChanges.compareAndSet(this, parsedChanges);
      return parsedChanges;
    }

    @NotNull
    private List<Change> parseMergedChanges() throws VcsException {
      List<MergedStatusInfo<S>> statuses = getMergedStatusInfo();
      List<Change> changes = parseStatusInfo(ContainerUtil.map(statuses, MergedStatusInfo::getStatusInfo), 0);
      LOG.assertTrue(changes.size() == statuses.size(), "Incorrectly parsed statuses " + statuses + " to changes " + changes);
      if (getParents().size() <= 1) return changes;

      // each merge change knows about all changes to parents
      List<Change> wrappedChanges = new ArrayList<>(statuses.size());
      for (int i = 0; i < statuses.size(); i++) {
        wrappedChanges.add(new MyMergedChange(changes.get(i), statuses.get(i)));
      }
      return wrappedChanges;
    }

    @NotNull
    @Override
    public Collection<Change> getMergedChanges() throws VcsException {
      return parseChanges().getMergedChanges();
    }

    @NotNull
    @Override
    public Collection<Change> getChanges(int parent) throws VcsException {
      return parseChanges().getChanges(parent);
    }

    @NotNull
    @Override
    public Collection<String> getModifiedPaths(int parent) {
      Set<String> changes = ContainerUtil.newHashSet();
      for (S status : myChangesOutput.get(parent)) {
        if (myDescriptor.getSecondPath(status) == null) {
          changes.add(absolutePath(myDescriptor.getFirstPath(status)));
        }
      }
      return changes;
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      Set<Couple<String>> renames = ContainerUtil.newHashSet();
      for (S status : myChangesOutput.get(parent)) {
        String secondPath = myDescriptor.getSecondPath(status);
        if (secondPath != null) {
          renames.add(Couple.of(absolutePath(myDescriptor.getFirstPath(status)), absolutePath(secondPath)));
        }
      }
      return renames;
    }

    @NotNull
    protected String absolutePath(@NotNull String path) {
      return getRoot().getPath() + "/" + path;
    }

    @NotNull
    private List<Collection<Change>> computeChanges(@NotNull Collection<Change> mergedChanges)
      throws VcsException {
      if (myChangesOutput.size() == 1) {
        return Collections.singletonList(mergedChanges);
      }
      else {
        List<Collection<Change>> changes = ContainerUtil.newArrayListWithCapacity(myChangesOutput.size());
        for (int i = 0; i < myChangesOutput.size(); i++) {
          changes.add(parseStatusInfo(myChangesOutput.get(i), i));
        }
        return changes;
      }
    }

    @NotNull
    protected abstract List<Change> parseStatusInfo(@NotNull List<S> changes, int parentIndex) throws VcsException;

    /*
     * This method mimics result of `-c` option added to `git log` command.
     * It calculates statuses for files that were modified in all parents of a merge commit.
     * If a commit is not a merge, all statuses are returned.
     */
    @NotNull
    private List<MergedStatusInfo<S>> getMergedStatusInfo() {
      return myDescriptor.getMergedStatusInfo(myChangesOutput);
    }

    private class MyMergedChange extends MergedChange {
      @NotNull private final MergedStatusInfo<S> myStatusInfo;
      @NotNull private final Supplier<List<Change>> mySourceChanges;

      MyMergedChange(@NotNull Change change, @NotNull MergedStatusInfo<S> statusInfo) {
        super(change);
        myStatusInfo = statusInfo;
        mySourceChanges = Suppliers.memoize(() -> {
          List<Change> sourceChanges = ContainerUtil.newArrayList();
          try {
            for (int parent = 0; parent < myStatusInfo.getMergedStatusInfos().size(); parent++) {
              sourceChanges.addAll(parseStatusInfo(Collections.singletonList(myStatusInfo.getMergedStatusInfos().get(parent)), parent));
            }
          }
          catch (VcsException e) {
            LOG.error(e);
          }
          return sourceChanges;
        });
      }

      @Override
      public List<Change> getSourceChanges() {
        return mySourceChanges.get();
      }
    }
  }

  public static class ParsedChanges implements Changes {
    @NotNull private final Collection<Change> myMergedChanges;
    @NotNull private final List<Collection<Change>> myChanges;

    ParsedChanges(@NotNull Collection<Change> mergedChanges, @NotNull List<Collection<Change>> changes) {
      myMergedChanges = mergedChanges;
      myChanges = changes;
    }

    @NotNull
    @Override
    public Collection<Change> getMergedChanges() {
      return myMergedChanges;
    }

    @NotNull
    @Override
    public Collection<Change> getChanges(int parent) {
      return myChanges.get(parent);
    }

    @NotNull
    @Override
    public Collection<String> getModifiedPaths(int parent) {
      Set<String> changes = ContainerUtil.newHashSet();

      for (Change change : getChanges(parent)) {
        if (!change.getType().equals(Change.Type.MOVED)) {
          if (change.getAfterRevision() != null) changes.add(change.getAfterRevision().getFile().getPath());
          if (change.getBeforeRevision() != null) changes.add(change.getBeforeRevision().getFile().getPath());
        }
      }

      return changes;
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      Set<Couple<String>> renames = ContainerUtil.newHashSet();
      for (Change change : getChanges(parent)) {
        if (change.getType().equals(Change.Type.MOVED)) {
          if (change.getAfterRevision() != null && change.getBeforeRevision() != null) {
            renames.add(Couple.of(change.getBeforeRevision().getFile().getPath(), change.getAfterRevision().getFile().getPath()));
          }
        }
      }
      return renames;
    }
  }
}
