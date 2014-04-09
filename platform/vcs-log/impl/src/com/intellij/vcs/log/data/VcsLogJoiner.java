/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Stanislav Erokhin
 * @author Kirill Likhodedov
 */
public class VcsLogJoiner {
  private final static int BOUND_SAVED_LOG = 10000;

  public final static String NOT_ENOUGH_FIRST_BLOCK = "Not enough first block";
  public final static String ILLEGAL_DATA_RELOAD_ALL = "All data is illegal - request reload all";

  /**
   * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
   *
   * @param savedLog       currently available part of the log.
   * @param previousRefs   references saved from the previous refresh.
   * @param firstBlock     the first n commits read from the VCS.
   * @param newRefs        all references (branches) of the repository.
   * @return Total saved log with new commits properly attached to it + number of new commits attached to the log.
   */
  @NotNull
  public Pair<List<TimedVcsCommit>, Integer> addCommits(@NotNull List<? extends TimedVcsCommit> savedLog,
                                                           @NotNull Collection<VcsRef> previousRefs,
                                                           @NotNull List<? extends TimedVcsCommit> firstBlock,
                                                           @NotNull Collection<VcsRef> newRefs) {
    Set<Hash> previousRefsHashes = toHashes(previousRefs);
    Set<Hash> newRefsHashes = toHashes(newRefs);
    Pair<Integer, Set<TimedVcsCommit>> newCommitsAndSavedGreenIndex =
      getNewCommitsAndSavedGreenIndex(savedLog, previousRefsHashes, firstBlock, newRefsHashes);
    Pair<Integer, Set<Hash>> redCommitsAndSavedRedIndex =
      getRedCommitsAndSavedRedIndex(savedLog, previousRefsHashes, firstBlock, newRefsHashes);

    Set<Hash> removeCommits = redCommitsAndSavedRedIndex.second;
    Set<TimedVcsCommit> allNewsCommits = newCommitsAndSavedGreenIndex.second;

    int unsafeBlockSize = Math.max(redCommitsAndSavedRedIndex.first, newCommitsAndSavedGreenIndex.first);
    List<TimedVcsCommit> unsafePartSavedLog = new ArrayList<TimedVcsCommit>();
    for (TimedVcsCommit commit : savedLog.subList(0, unsafeBlockSize)) {
      if (!removeCommits.contains(commit.getHash())) {
        unsafePartSavedLog.add(commit);
      }
    }
    unsafePartSavedLog = new NewCommitIntegrator<TimedVcsCommit>(unsafePartSavedLog, allNewsCommits).getResultList();

    return Pair.create(ContainerUtil.concat(unsafePartSavedLog, savedLog.subList(unsafeBlockSize, savedLog.size())),
                       unsafePartSavedLog.size() - unsafeBlockSize);
  }

  private static Set<Hash> toHashes(Collection<VcsRef> refs) {
    Set<Hash> hashes = new java.util.HashSet<Hash>(refs.size());
    for (VcsRef ref : refs) {
      hashes.add(ref.getCommitHash());
    }
    return hashes;
  }

  @NotNull
  private static Pair<Integer, Set<TimedVcsCommit>> getNewCommitsAndSavedGreenIndex(@NotNull List<? extends TimedVcsCommit> savedLog,
                                                                                    @NotNull Collection<Hash> previousRefs,
                                                                                    @NotNull List<? extends TimedVcsCommit> firstBlock,
                                                                                    @NotNull Collection<Hash> newRefs) {
    Set<Hash> allUnresolvedLinkedHashes = new HashSet<Hash>(newRefs);
    allUnresolvedLinkedHashes.removeAll(previousRefs);
    // at this moment allUnresolvedLinkedHashes contains only NEW refs
    for (VcsCommit commit : firstBlock) {
      allUnresolvedLinkedHashes.add(commit.getHash());
      allUnresolvedLinkedHashes.addAll(commit.getParents());
    }
    for (VcsCommit commit : firstBlock) {
      if (commit.getParents().size() != 0) {
        allUnresolvedLinkedHashes.remove(commit.getHash());
      }
    }
    int saveGreenIndex = getFirstUnTrackedIndex(savedLog, allUnresolvedLinkedHashes);

    return new Pair<Integer, Set<TimedVcsCommit>>(saveGreenIndex, getAllNewCommits(savedLog.subList(0, saveGreenIndex), firstBlock));
  }

  private static int getFirstUnTrackedIndex(@NotNull List<? extends TimedVcsCommit> commits, @NotNull Set<Hash> searchHashes) {
    int lastIndex;
    for (lastIndex = 0; lastIndex < commits.size(); lastIndex++) {
      VcsCommit commit = commits.get(lastIndex);
      if (searchHashes.size() == 0)
        return lastIndex;
      if (lastIndex > BOUND_SAVED_LOG)
        throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL);
      searchHashes.remove(commit.getHash());
    }
    if (searchHashes.size() != 0)
      throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL);
    return lastIndex;
  }

  private static Set<TimedVcsCommit> getAllNewCommits(@NotNull List<? extends TimedVcsCommit> unsafeGreenPartSavedLog,
                                                      @NotNull List<? extends TimedVcsCommit> firstBlock) {
    Set<Hash> existedCommitHashes = ContainerUtil.newHashSet();
    for (VcsCommit commit : unsafeGreenPartSavedLog) {
      existedCommitHashes.add(commit.getHash());
    }
    Set<TimedVcsCommit> allNewsCommits = ContainerUtil.newHashSet();
    for (TimedVcsCommit newCommit : firstBlock) {
      if (!existedCommitHashes.contains(newCommit.getHash())) {
        allNewsCommits.add(newCommit);
      }
    }
    return allNewsCommits;
  }

  @NotNull
  private static Pair<Integer, Set<Hash>> getRedCommitsAndSavedRedIndex(@NotNull List<? extends TimedVcsCommit> savedLog,
                                                                     @NotNull Collection<Hash> previousRefs,
                                                                     @NotNull List<? extends TimedVcsCommit> firstBlock,
                                                                     @NotNull Collection<Hash> newRefs) {
    Set<Hash> startRedCommits = new HashSet<Hash>(previousRefs);
    startRedCommits.removeAll(newRefs);
    Set<Hash> startGreenNodes = new HashSet<Hash>(newRefs);
    for (TimedVcsCommit commit : firstBlock) {
      startGreenNodes.add(commit.getHash());
      startGreenNodes.addAll(commit.getParents());
    }
    RedGreenSorter sorter = new RedGreenSorter(startRedCommits, startGreenNodes, savedLog);
    int saveRegIndex = sorter.getFirstSaveIndex();

    return new Pair<Integer, Set<Hash>>(saveRegIndex, sorter.getAllRedCommit());
  }

  private static class RedGreenSorter {
    private final Set<Hash> currentRed;
    private final Set<Hash> currentGreen;
    private final Set<Hash> allRedCommit = new HashSet<Hash>();

    private final List<? extends TimedVcsCommit> savedLog;

    private RedGreenSorter(Set<Hash> startRedNodes, Set<Hash> startGreenNodes, List<? extends TimedVcsCommit> savedLog) {
      this.currentRed = startRedNodes;
      this.currentGreen = startGreenNodes;
      this.savedLog = savedLog;
    }

    private void markRealRedNode(@NotNull Hash node) {
      if (!currentRed.remove(node))
        throw new IllegalStateException(NOT_ENOUGH_FIRST_BLOCK);
      allRedCommit.add(node);
    }

    private int getFirstSaveIndex() {
      for (int lastIndex = 0; lastIndex < savedLog.size(); lastIndex++) {
        TimedVcsCommit commit = savedLog.get(lastIndex);
        if (lastIndex > BOUND_SAVED_LOG)
          throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL);

        boolean isGreen = currentGreen.contains(commit.getHash());
        if (isGreen) {
          currentRed.remove(commit.getHash());
          currentGreen.addAll(commit.getParents());
        }
        else {
          markRealRedNode(commit.getHash());
          currentRed.addAll(commit.getParents());
        }

        if (currentRed.isEmpty())
          return lastIndex + 1;
      }
      throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL);
    }

    public Set<Hash> getAllRedCommit() {
      return allRedCommit;
    }
  }


  static class NewCommitIntegrator<Commit extends TimedVcsCommit> {
    private final List<Commit> list;
    private final Map<Hash, Commit> newCommitsMap;

    private final Stack<Commit> commitsStack;

    public NewCommitIntegrator(@NotNull List<Commit> list, @NotNull Collection<Commit> newCommits) {
      this.list = list;
      newCommitsMap = ContainerUtil.newHashMap();
      for (Commit commit : newCommits) {
        newCommitsMap.put(commit.getHash(), commit);
      }
      commitsStack = new Stack<Commit>();
    }

    private void insertAllUseStack() {
      while (!newCommitsMap.isEmpty()) {
        commitsStack.push(newCommitsMap.values().iterator().next());
        while (!commitsStack.isEmpty()) {
          Commit currentCommit = commitsStack.peek();
          boolean allParentsWereAdded = true;
          for (Hash parentHash : currentCommit.getParents()) {
            Commit parentCommit = newCommitsMap.get(parentHash);
            if (parentCommit != null) {
              commitsStack.push(parentCommit);
              allParentsWereAdded = false;
              break;
            }
          }

          if (!allParentsWereAdded)
            continue;

          int insertIndex;
          HashSet<Hash> parents = new HashSet<Hash>(currentCommit.getParents());
          for (insertIndex = 0; insertIndex < list.size(); insertIndex++) {
            Commit someCommit = list.get(insertIndex);
            if (parents.contains(someCommit.getHash()))
              break;
            if (someCommit.getTime() < currentCommit.getTime())
              break;
          }

          list.add(insertIndex, currentCommit);
          newCommitsMap.remove(currentCommit.getHash());
          commitsStack.pop();
        }
      }
    }

    @NotNull
    public List<Commit> getResultList() {
      insertAllUseStack();
      return list;
    }
  }


}
