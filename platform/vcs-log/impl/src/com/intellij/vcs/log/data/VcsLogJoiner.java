// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Attaches the block of the latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Stanislav Erokhin
 * @author Kirill Likhodedov
 */
public final class VcsLogJoiner<CommitId, Commit extends GraphCommit<CommitId>> {
  public static final @NonNls String ILLEGAL_DATA_RELOAD_ALL = "All data is illegal - request reload all";

  /**
   * Attaches the block of the latest commits, which was read from the VCS, to the existing log structure.
   *
   * @param savedLog     currently available part of the log.
   * @param previousRefs references saved from the previous refresh.
   * @param firstBlock   the first n commits read from the VCS.
   * @param newRefs      all references (branches) of the repository.
   * @return Total saved log with new commits properly attached to it + number of new commits attached to the log.
   */
  public @NotNull Pair<List<Commit>, Integer> addCommits(@NotNull List<? extends Commit> savedLog,
                                                @NotNull Collection<? extends CommitId> previousRefs,
                                                @NotNull List<? extends Commit> firstBlock,
                                                @NotNull Collection<? extends CommitId> newRefs) {
    Pair<Integer, Set<Commit>> newCommitsAndSavedGreenIndex =
      getNewCommitsAndSavedGreenIndex(savedLog, previousRefs, firstBlock, newRefs);
    Pair<Integer, Set<CommitId>> redCommitsAndSavedRedIndex =
      getRedCommitsAndSavedRedIndex(savedLog, previousRefs, firstBlock, newRefs);

    Set<CommitId> removeCommits = redCommitsAndSavedRedIndex.second;
    Set<Commit> allNewsCommits = newCommitsAndSavedGreenIndex.second;

    int unsafeBlockSize = Math.max(redCommitsAndSavedRedIndex.first, newCommitsAndSavedGreenIndex.first);
    List<Commit> unsafePartSavedLog = new ArrayList<>();
    for (Commit commit : savedLog.subList(0, unsafeBlockSize)) {
      if (!removeCommits.contains(commit.getId())) {
        unsafePartSavedLog.add(commit);
      }
    }
    unsafePartSavedLog = new NewCommitIntegrator<>(unsafePartSavedLog, allNewsCommits).getResultList();

    return Pair.create(ContainerUtil.concat(unsafePartSavedLog, savedLog.subList(unsafeBlockSize, savedLog.size())),
                       unsafePartSavedLog.size() - unsafeBlockSize);
  }


  private @NotNull Pair<Integer, Set<Commit>> getNewCommitsAndSavedGreenIndex(@NotNull List<? extends Commit> savedLog,
                                                                              @NotNull Collection<? extends CommitId> previousRefs,
                                                                              @NotNull List<? extends Commit> firstBlock,
                                                                              @NotNull Collection<? extends CommitId> newRefs) {
    Set<CommitId> allUnresolvedLinkedHashes = new HashSet<>(newRefs);
    allUnresolvedLinkedHashes.removeAll(previousRefs);
    // at this moment allUnresolvedLinkedHashes contains only NEW refs
    for (Commit commit : firstBlock) {
      allUnresolvedLinkedHashes.add(commit.getId());
      allUnresolvedLinkedHashes.addAll(commit.getParents());
    }
    for (Commit commit : firstBlock) {
      if (!commit.getParents().isEmpty()) {
        allUnresolvedLinkedHashes.remove(commit.getId());
      }
    }
    int saveGreenIndex = getFirstUnTrackedIndex(savedLog, allUnresolvedLinkedHashes);

    return new Pair<>(saveGreenIndex, getAllNewCommits(savedLog.subList(0, saveGreenIndex), firstBlock));
  }

  private int getFirstUnTrackedIndex(@NotNull List<? extends Commit> commits, @NotNull Set<CommitId> searchHashes) {
    int lastIndex;
    for (lastIndex = 0; lastIndex < commits.size(); lastIndex++) {
      Commit commit = commits.get(lastIndex);
      if (searchHashes.isEmpty()) {
        return lastIndex;
      }
      searchHashes.remove(commit.getId());
    }
    if (!searchHashes.isEmpty()) {
      throw new VcsLogRefreshNotEnoughDataException();
    }
    return lastIndex;
  }

  private Set<Commit> getAllNewCommits(@NotNull List<? extends Commit> unsafeGreenPartSavedLog,
                                       @NotNull List<? extends Commit> firstBlock) {
    Set<CommitId> existedCommitHashes = new HashSet<>();
    for (Commit commit : unsafeGreenPartSavedLog) {
      existedCommitHashes.add(commit.getId());
    }
    Set<Commit> allNewsCommits = new HashSet<>();
    for (Commit newCommit : firstBlock) {
      if (!existedCommitHashes.contains(newCommit.getId())) {
        allNewsCommits.add(newCommit);
      }
    }
    return allNewsCommits;
  }

  private @NotNull Pair<Integer, Set<CommitId>> getRedCommitsAndSavedRedIndex(@NotNull List<? extends Commit> savedLog,
                                                                              @NotNull Collection<? extends CommitId> previousRefs,
                                                                              @NotNull List<? extends Commit> firstBlock,
                                                                              @NotNull Collection<? extends CommitId> newRefs) {
    Set<CommitId> startRedCommits = new HashSet<>(previousRefs);
    startRedCommits.removeAll(newRefs);
    Set<CommitId> startGreenNodes = new HashSet<>(newRefs);
    for (Commit commit : firstBlock) {
      startGreenNodes.add(commit.getId());
      startGreenNodes.addAll(commit.getParents());
    }
    RedGreenSorter<CommitId, Commit> sorter = new RedGreenSorter<>(startRedCommits, startGreenNodes, savedLog);
    int saveRegIndex = sorter.getFirstSaveIndex();

    return new Pair<>(saveRegIndex, sorter.getAllRedCommit());
  }

  private static final class RedGreenSorter<CommitId, Commit extends GraphCommit<CommitId>> {
    private final Set<? super CommitId> currentRed;
    private final Set<? super CommitId> currentGreen;
    private final Set<CommitId> allRedCommit = new HashSet<>();

    private final List<? extends Commit> savedLog;

    private RedGreenSorter(Set<? super CommitId> startRedNodes, Set<? super CommitId> startGreenNodes, List<? extends Commit> savedLog) {
      this.currentRed = startRedNodes;
      this.currentGreen = startGreenNodes;
      this.savedLog = savedLog;
    }

    private void markRealRedNode(@NotNull CommitId node) {
      if (!currentRed.remove(node)) {
        throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL); // see VcsLogJoinerTest#illegalStateExceptionTest2
      }
      allRedCommit.add(node);
    }

    private int getFirstSaveIndex() {
      for (int lastIndex = 0; lastIndex < savedLog.size(); lastIndex++) {
        Commit commit = savedLog.get(lastIndex);
        boolean isGreen = currentGreen.contains(commit.getId());
        if (isGreen) {
          currentRed.remove(commit.getId());
          currentGreen.addAll(commit.getParents());
        }
        else {
          markRealRedNode(commit.getId());
          currentRed.addAll(commit.getParents());
        }

        if (currentRed.isEmpty()) {
          return lastIndex + 1;
        }
      }
      throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL); // see VcsLogJoinerTest#illegalStateExceptionTest
    }

    public Set<CommitId> getAllRedCommit() {
      return allRedCommit;
    }
  }


  static class NewCommitIntegrator<CommitId, Commit extends GraphCommit<CommitId>> {
    private final List<Commit> list;
    private final Map<CommitId, Commit> newCommitsMap;

    private final Stack<Commit> commitsStack;

    NewCommitIntegrator(@NotNull List<Commit> list, @NotNull Collection<Commit> newCommits) {
      this.list = list;
      newCommitsMap = new HashMap<>();
      for (Commit commit : newCommits) {
        newCommitsMap.put(commit.getId(), commit);
      }
      commitsStack = new Stack<>();
    }

    private void insertAllUseStack() {
      while (!newCommitsMap.isEmpty()) {
        visitCommit(Objects.requireNonNull(ContainerUtil.getFirstItem(newCommitsMap.values())));
        while (!commitsStack.isEmpty()) {
          Commit currentCommit = commitsStack.peek();
          boolean allParentsWereAdded = true;
          for (CommitId parentHash : currentCommit.getParents()) {
            Commit parentCommit = newCommitsMap.get(parentHash);
            if (parentCommit != null) {
              visitCommit(parentCommit);
              allParentsWereAdded = false;
              break;
            }
          }

          if (!allParentsWereAdded) {
            continue;
          }

          int insertIndex;
          Set<CommitId> parents = new HashSet<>(currentCommit.getParents());
          for (insertIndex = 0; insertIndex < list.size(); insertIndex++) {
            Commit someCommit = list.get(insertIndex);
            if (parents.contains(someCommit.getId())) {
              break;
            }
            if (someCommit.getTimestamp() < currentCommit.getTimestamp()) {
              break;
            }
          }

          list.add(insertIndex, currentCommit);
          commitsStack.pop();
        }
      }
    }

    private void visitCommit(@NotNull Commit commit) {
      commitsStack.push(commit);
      newCommitsMap.remove(commit.getId());
    }

    public @NotNull List<Commit> getResultList() {
      insertAllUseStack();
      return list;
    }
  }
}
