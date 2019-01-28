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
import com.intellij.util.containers.WeakStringInterner;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsStatusMerger.MergedStatusInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allows to postpone changes parsing, which might take long for a large amount of commits,
 * because {@link Change} holds {@link LocalFilePath} which makes costly refreshes and type detections.
 */
public abstract class VcsChangesLazilyParsedDetails extends VcsCommitMetadataImpl implements VcsFullCommitDetails, VcsIndexableDetails {
  private static final Logger LOG = Logger.getInstance(VcsChangesLazilyParsedDetails.class);
  private static final WeakStringInterner ourPathsInterner = new WeakStringInterner();
  protected static final Changes EMPTY_CHANGES = new EmptyChanges();
  @NotNull protected final AtomicReference<Changes> myChanges = new AtomicReference<>();

  public VcsChangesLazilyParsedDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                                       @NotNull String subject, @NotNull VcsUser author, @NotNull String message,
                                       @NotNull VcsUser committer, long authorTime) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
  }

  @NotNull
  @Override
  public Map<String, Change.Type> getModifiedPaths(int parent) {
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
    Map<String, Change.Type> getModifiedPaths(int parent);

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
    public Map<String, Change.Type> getModifiedPaths(int parent) {
      return Collections.emptyMap();
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      return ContainerUtil.emptyList();
    }
  }

  protected abstract class UnparsedChanges implements Changes {
    @NotNull protected final Project myProject;
    // without interner each commit will have it's own instance of this string
    @NotNull private final String myRootPrefix = ourPathsInterner.intern(getRoot().getPath() + "/");
    @NotNull protected final List<List<VcsFileStatusInfo>> myChangesOutput;
    @NotNull private final VcsStatusMerger<VcsFileStatusInfo> myStatusMerger = new VcsFileStatusInfoMerger();

    public UnparsedChanges(@NotNull Project project,
                           @NotNull List<List<VcsFileStatusInfo>> changesOutput) {
      myProject = project;
      myChangesOutput = changesOutput;
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
      List<MergedStatusInfo<VcsFileStatusInfo>> statuses = getMergedStatusInfo();
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
    public Map<String, Change.Type> getModifiedPaths(int parent) {
      Map<String, Change.Type> changes = ContainerUtil.newHashMap();
      for (VcsFileStatusInfo status : myChangesOutput.get(parent)) {
        String secondPath = status.getSecondPath();
        if (secondPath == null) {
          changes.put(absolutePath(status.getFirstPath()), status.getType());
        }
      }
      return changes;
    }

    @NotNull
    @Override
    public Collection<Couple<String>> getRenamedPaths(int parent) {
      Set<Couple<String>> renames = ContainerUtil.newHashSet();
      for (VcsFileStatusInfo status : myChangesOutput.get(parent)) {
        String secondPath = status.getSecondPath();
        if (secondPath != null) {
          renames.add(Couple.of(absolutePath(status.getFirstPath()), absolutePath(secondPath)));
        }
      }
      return renames;
    }

    @NotNull
    private String absolutePath(@NotNull String path) {
      return myRootPrefix + path;
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
    protected abstract List<Change> parseStatusInfo(@NotNull List<VcsFileStatusInfo> changes, int parentIndex) throws VcsException;

    /*
     * This method mimics result of `-c` option added to `git log` command.
     * It calculates statuses for files that were modified in all parents of a merge commit.
     * If a commit is not a merge, all statuses are returned.
     */
    @NotNull
    private List<MergedStatusInfo<VcsFileStatusInfo>> getMergedStatusInfo() {
      return myStatusMerger.merge(myChangesOutput);
    }

    private class MyMergedChange extends MergedChange {
      @NotNull private final MergedStatusInfo<VcsFileStatusInfo> myStatusInfo;
      @NotNull private final Supplier<List<Change>> mySourceChanges;

      MyMergedChange(@NotNull Change change, @NotNull MergedStatusInfo<VcsFileStatusInfo> statusInfo) {
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
    @NotNull private final List<? extends Collection<Change>> myChanges;

    ParsedChanges(@NotNull Collection<Change> mergedChanges, @NotNull List<? extends Collection<Change>> changes) {
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
    public Map<String, Change.Type> getModifiedPaths(int parent) {
      Map<String, Change.Type> changes = ContainerUtil.newHashMap();

      for (Change change : getChanges(parent)) {
        Change.Type type = change.getType();
        if (!type.equals(Change.Type.MOVED)) {
          if (change.getAfterRevision() != null) changes.put(change.getAfterRevision().getFile().getPath(), type);
          if (change.getBeforeRevision() != null) changes.put(change.getBeforeRevision().getFile().getPath(), type);
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
