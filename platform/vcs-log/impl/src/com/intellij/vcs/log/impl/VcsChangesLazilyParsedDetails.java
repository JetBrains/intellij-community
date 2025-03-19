// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsStatusMerger.MergedStatusInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * Allows to postpone changes parsing, which might take long for a large amount of commits,
 * because {@link Change} holds {@link LocalFilePath} which makes costly refreshes and type detections.
 */
@ApiStatus.Experimental
public class VcsChangesLazilyParsedDetails extends VcsCommitMetadataImpl implements VcsFullCommitDetails {
  private static final Logger LOG = Logger.getInstance(VcsChangesLazilyParsedDetails.class);
  protected static final Changes EMPTY_CHANGES = new EmptyChanges();
  private final @NotNull ChangesParser myChangesParser;
  private final @NotNull AtomicReference<Changes> myChanges = new AtomicReference<>();

  public VcsChangesLazilyParsedDetails(@NotNull Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime,
                                       @NotNull VirtualFile root,
                                       @NotNull String subject, @NotNull VcsUser author, @NotNull String message,
                                       @NotNull VcsUser committer, long authorTime,
                                       @NotNull List<List<VcsFileStatusInfo>> reportedChanges,
                                       @NotNull ChangesParser changesParser) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime);
    myChangesParser = changesParser;
    myChanges.set(reportedChanges.isEmpty() ? EMPTY_CHANGES : new UnparsedChanges(project, reportedChanges, (sources, parent) -> {
      return myChangesParser.parseStatusInfo(project, this, sources, parent);
    }));
  }

  @Override
  public @NotNull Collection<Change> getChanges() {
    return myChanges.get().getMergedChanges();
  }

  @Override
  public @NotNull Collection<Change> getChanges(int parent) {
    return myChanges.get().getChanges(parent);
  }

  public int size() {
    return myChanges.get().size();
  }

  protected @NotNull Changes getChangesObject() {
    return myChanges.get();
  }

  protected interface Changes {
    @NotNull
    Collection<Change> getMergedChanges();

    @NotNull
    Collection<Change> getChanges(int parent);

    int size();
  }

  protected static class EmptyChanges implements Changes {
    @Override
    public @NotNull Collection<Change> getMergedChanges() {
      return ContainerUtil.emptyList();
    }

    @Override
    public @NotNull Collection<Change> getChanges(int parent) {
      return ContainerUtil.emptyList();
    }

    @Override
    public int size() {
      return 0;
    }
  }

  protected class UnparsedChanges implements Changes {
    protected final @NotNull Project myProject;
    protected final @NotNull List<List<VcsFileStatusInfo>> myChangesOutput;
    private final @NotNull VcsStatusMerger<VcsFileStatusInfo, CharSequence> myStatusMerger = new VcsFileStatusInfoMerger();
    private final @NotNull BiFunction<List<VcsFileStatusInfo>, Integer, List<Change>> myParser;

    public UnparsedChanges(@NotNull Project project,
                           @NotNull List<List<VcsFileStatusInfo>> changesOutput,
                           @NotNull BiFunction<List<VcsFileStatusInfo>, Integer, List<Change>> parser) {
      myProject = project;
      myChangesOutput = changesOutput;
      myParser = parser;
    }

    protected @NotNull ParsedChanges parseChanges() {
      List<Change> mergedChanges = parseMergedChanges();
      List<Collection<Change>> changes = computeChanges(mergedChanges);
      ParsedChanges parsedChanges = new ParsedChanges(mergedChanges, changes);
      myChanges.compareAndSet(this, parsedChanges);
      return parsedChanges;
    }

    private @NotNull List<Change> parseMergedChanges() {
      List<MergedStatusInfo<VcsFileStatusInfo>> statuses = getMergedStatusInfo();
      List<Change> changes = myParser.apply(ContainerUtil.map(statuses, MergedStatusInfo::getStatusInfo), 0);
      if (changes.size() != statuses.size()) {
        LOG.error("Incorrectly parsed statuses " + statuses + " to changes " + changes);
      }
      if (getParents().size() <= 1) return changes;

      // each merge change knows about all changes to parents
      List<Change> wrappedChanges = new ArrayList<>(statuses.size());
      for (int i = 0; i < statuses.size(); i++) {
        wrappedChanges.add(new MyMergedChange(changes.get(i), statuses.get(i), myParser));
      }
      return wrappedChanges;
    }

    @Override
    public @NotNull Collection<Change> getMergedChanges() {
      return parseChanges().getMergedChanges();
    }

    @Override
    public @NotNull Collection<Change> getChanges(int parent) {
      return parseChanges().getChanges(parent);
    }

    @Override
    public int size() {
      int size = 0;
      for (List<VcsFileStatusInfo> changesToParent : myChangesOutput) {
        size += changesToParent.size();
      }
      return size;
    }

    private @NotNull List<Collection<Change>> computeChanges(@NotNull Collection<Change> mergedChanges) {
      if (myChangesOutput.size() == 1) {
        return Collections.singletonList(mergedChanges);
      }
      else {
        List<Collection<Change>> changes = new ArrayList<>(myChangesOutput.size());
        for (int i = 0; i < myChangesOutput.size(); i++) {
          ProgressManager.checkCanceled();
          changes.add(myParser.apply(myChangesOutput.get(i), i));
        }
        return changes;
      }
    }

    /*
     * This method mimics result of `-c` option added to `git log` command.
     * It calculates statuses for files that were modified in all parents of a merge commit.
     * If a commit is not a merge, all statuses are returned.
     */
    private @NotNull List<MergedStatusInfo<VcsFileStatusInfo>> getMergedStatusInfo() {
      return myStatusMerger.merge(myChangesOutput);
    }

    @ApiStatus.Internal
    public @NotNull Collection<VcsFileStatusInfo> getMergedStatuses() {
      Collection<VcsFileStatusInfo> result = new HashSet<>();
      for (MergedStatusInfo<VcsFileStatusInfo> mergedStatusInfo : getMergedStatusInfo()) {
        result.add(mergedStatusInfo.getStatusInfo());
      }
      return result;
    }
  }

  private static class MyMergedChange extends MergedChange {
    private final @NotNull Supplier<List<Change>> mySourceChanges;

    MyMergedChange(@NotNull Change change, @NotNull MergedStatusInfo<VcsFileStatusInfo> statusInfo,
                   @NotNull BiFunction<List<VcsFileStatusInfo>, Integer, List<Change>> parser) {
      super(change);
      mySourceChanges = Suppliers.memoize(() -> {
        List<Change> sourceChanges = new ArrayList<>();
        for (int parent = 0; parent < statusInfo.getMergedStatusInfos().size(); parent++) {
          List<VcsFileStatusInfo> statusInfos = Collections.singletonList(statusInfo.getMergedStatusInfos().get(parent));
          sourceChanges.addAll(parser.apply(statusInfos, parent));
        }
        return sourceChanges;
      });
    }

    @Override
    public List<Change> getSourceChanges() {
      return mySourceChanges.get();
    }
  }

  protected static class ParsedChanges implements Changes {
    private final @NotNull Collection<Change> myMergedChanges;
    private final @NotNull List<? extends Collection<Change>> myChanges;

    ParsedChanges(@NotNull Collection<Change> mergedChanges, @NotNull List<? extends Collection<Change>> changes) {
      myMergedChanges = mergedChanges;
      myChanges = changes;
    }

    @Override
    public @NotNull Collection<Change> getMergedChanges() {
      return myMergedChanges;
    }

    @Override
    public @NotNull Collection<Change> getChanges(int parent) {
      return myChanges.get(parent);
    }

    @Override
    public int size() {
      int size = 0;
      for (Collection<Change> changesToParent : myChanges) {
        size += changesToParent.size();
      }
      return size;
    }
  }

  public interface ChangesParser {
    List<Change> parseStatusInfo(@NotNull Project project,
                                 @NotNull VcsShortCommitDetails commit,
                                 @NotNull List<VcsFileStatusInfo> changes,
                                 int parentIndex);
  }
}
