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
    Pair<Integer, Set<Hash>> redCommitsAndSavedRedIndex =
      getRedCommitsAndSavedRedIndex(savedLog, previousRefsHashes, firstBlock, newRefsHashes);
    Pair<Integer, Set<TimedVcsCommit>> newCommitsAndSavedGreenIndex =
      getNewCommitsAndSavedGreenIndex(savedLog, previousRefsHashes, firstBlock, newRefsHashes);

    if (redCommitsAndSavedRedIndex.first == -1) { // firstBlock not enough or remove old commits
      throw new IllegalStateException(); //todo
    }
    else if (newCommitsAndSavedGreenIndex.first == -1) {
      throw new IllegalStateException(); // todo
    }
    Set<Hash> removeCommits = redCommitsAndSavedRedIndex.second;
    Set<TimedVcsCommit> allNewsCommits = newCommitsAndSavedGreenIndex.second;

    int unsafeBlockSize = Math.max(redCommitsAndSavedRedIndex.first, newCommitsAndSavedGreenIndex.first);
    List<TimedVcsCommit> unsafePartSavedLog = new ArrayList<TimedVcsCommit>();
    for (TimedVcsCommit commit : savedLog.subList(0, unsafeBlockSize)) {
      if (!removeCommits.contains(commit.getHash())) {
        unsafePartSavedLog.add(commit);
      }
    }
    unsafePartSavedLog = new NewCommitIntegrator(unsafePartSavedLog, allNewsCommits).getResultList();

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

  // return Pair(-1, null) if something bad :'(
  @NotNull
  private static Pair<Integer, Set<TimedVcsCommit>> getNewCommitsAndSavedGreenIndex(@NotNull List<? extends TimedVcsCommit> savedLog,
                                                                                    @NotNull Collection<Hash> previousRefs,
                                                                                    @NotNull List<? extends TimedVcsCommit> firstBlock,
                                                                                    @NotNull Collection<Hash> newRefs) {
    Set<Hash> allUnresolvedLinkedHashes = new HashSet<Hash>(newRefs);
    allUnresolvedLinkedHashes.removeAll(previousRefs);
    // in this moment allUnresolvedLinkedHashes contains only NEW refs
    for (VcsCommit commit : firstBlock) {
      allUnresolvedLinkedHashes.addAll(commit.getParents());
    }
    for (VcsCommit commit : firstBlock) {
      if (commit.getParents().size() != 0) {
        allUnresolvedLinkedHashes.remove(commit.getHash());
      }
    }
    int saveGreenIndex = getFirstUnTrackedIndex(savedLog, allUnresolvedLinkedHashes);
    if (saveGreenIndex == -1)
      return new Pair<Integer, Set<TimedVcsCommit>>(-1, null);

    return new Pair<Integer, Set<TimedVcsCommit>>(saveGreenIndex, getAllNewCommits(savedLog.subList(0, saveGreenIndex), firstBlock));
  }

  private static int getFirstUnTrackedIndex(@NotNull List<? extends TimedVcsCommit> commits, @NotNull Set<Hash> searchHashes) {
    int lastIndex = 0;
    for (VcsCommit commit : commits) {
      if (searchHashes.size() == 0)
        return lastIndex;
      if (lastIndex > BOUND_SAVED_LOG)
        return -1;
      searchHashes.remove(commit.getHash());
      lastIndex++;
    }
    return searchHashes.size() == 0 ? lastIndex : -1;
  }

  private static Set<TimedVcsCommit> getAllNewCommits(@NotNull List<? extends TimedVcsCommit> unsafeGreenPartSavedLog,
                                                      @NotNull List<? extends TimedVcsCommit> firstBlock) {
    Set<Hash> existedCommitHashes = new HashSet<Hash>();
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

  // return Pair(-1, null) if something bad :'(
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
    if (saveRegIndex == -1)
      return new Pair<Integer, Set<Hash>>(-1, null);

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
      assert currentRed.remove(node) : "Red node isn't marked as red";
      allRedCommit.add(node);
    }

    // return -1 if something bad
    private int getFirstSaveIndex() {
      int lastIndex = 0;
      for (TimedVcsCommit commit : savedLog) {
        if (lastIndex > BOUND_SAVED_LOG)
          return -1;

        boolean isGreen = currentGreen.remove(commit.getHash());
        if (isGreen) {
          currentRed.remove(commit.getHash());
          currentGreen.addAll(commit.getParents());
        } else {
          markRealRedNode(commit.getHash());
          currentRed.addAll(commit.getParents());
        }

        lastIndex++;
        if (currentRed.isEmpty())
          return lastIndex;
      }
      return -1;
    }

    public Set<Hash> getAllRedCommit() {
      return allRedCommit;
    }
  }


  private static class NewCommitIntegrator {
    private final List<TimedVcsCommit> list;
    private final Map<Hash, TimedVcsCommit> newCommitsMap;
    private final List<TimedVcsCommit> newSortedCommits;

    private NewCommitIntegrator(@NotNull List<TimedVcsCommit> list, @NotNull Set<TimedVcsCommit> newCommits) {
      this.list = list;
      newCommitsMap = ContainerUtil.newHashMap();
      for (TimedVcsCommit commit : newCommits) {
        newCommitsMap.put(commit.getHash(), commit);
      }
      newSortedCommits = new ArrayList<TimedVcsCommit>(newCommits);
      Collections.sort(newSortedCommits, new Comparator<TimedVcsCommit>() {
        @Override
        public int compare(@NotNull TimedVcsCommit o1, @NotNull TimedVcsCommit o2) {
          return new Long(o1.getTime()).compareTo(o2.getTime());
        }
      });
    }

    // return insert Index
    private void insertToList(@NotNull TimedVcsCommit commit) {
      if (!newCommitsMap.containsKey(commit.getHash())) {
        throw new IllegalStateException("Commit was inserted, but insert call again. Commit hash: " + commit.getHash());
      }
      //insert all parents commits
      for (Hash parentHash : commit.getParents()) {
        TimedVcsCommit parentCommit = newCommitsMap.get(parentHash);
        if (parentCommit != null) {
          insertToList(parentCommit);
        }
      }

      list.add(0, commit);
      newCommitsMap.remove(commit.getHash());
    }

    private void insertAllCommits() {
      for (TimedVcsCommit commit : newSortedCommits) {
        if (newCommitsMap.get(commit.getHash()) != null) {
          insertToList(commit);
        }
      }
    }

    private List<TimedVcsCommit> getResultList() {
      insertAllCommits();
      return list;
    }
  }


}
