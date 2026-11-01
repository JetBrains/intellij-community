// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.util.StopWatch;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Attaches the block of the latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Stanislav Erokhin
 * @author Kirill Likhodedov
 */
@ApiStatus.Internal
public final class VcsLogJoiner<CommitId, Commit extends GraphCommit<CommitId>> {
  private static final Logger LOG = Logger.getInstance(VcsLogJoiner.class);
  
  public static final @NonNls String ILLEGAL_DATA_RELOAD_ALL = "All data is illegal - request reload all";

  /**
   * Attaches the block of the latest commits, which was read from the VCS, to the existing log structure.
   *
   * @param savedLog     the previously computed log, as currently displayed/cached; some of its commits may need
   *                     to be replaced with fresher data from {@code firstBlock}, or removed entirely if they are
   *                     no longer reachable from any ref.
   * @param previousRefs references saved from the previous refresh.
   * @param firstBlock   the freshly re-read head of the log from the VCS (the most recent commits, newest first);
   *                     overlaps with the head of {@code savedLog} and may contain updated versions of commits
   *                     already present there (e.g. with changed parents), as well as genuinely new commits.
   * @param newRefs      all references (branches) of the repository.
   * @return Total saved log with new commits properly attached to it + number of new commits attached to the log.
   */
  public @NotNull Pair<List<Commit>, Integer> addCommits(@NotNull List<? extends Commit> savedLog,
                                                         @NotNull Collection<? extends CommitId> previousRefs,
                                                         @NotNull List<? extends Commit> firstBlock,
                                                         @NotNull Collection<? extends CommitId> newRefs) {
    StopWatch stopWatch = StopWatch.start("VcsLogJoiner.addCommits");
    try {
      Pair<Integer, Set<Commit>> newCommitsAndSavedGreenIndex =
        getNewCommitsAndSavedGreenIndex(savedLog, previousRefs, firstBlock, newRefs);
      Pair<Integer, Set<CommitId>> redCommitsAndSavedRedIndex =
        getRedCommitsAndSavedRedIndex(savedLog, previousRefs, firstBlock, newRefs);

      Set<CommitId> removeCommits = redCommitsAndSavedRedIndex.second;
      Set<Commit> allNewsCommits = newCommitsAndSavedGreenIndex.second;

      int unsafeBlockSize = Math.max(redCommitsAndSavedRedIndex.first, newCommitsAndSavedGreenIndex.first);
      // StopWatch does not make its span current, so these attributes go to the span of the caller
      Span.current()
        .setAttribute("savedLogSize", savedLog.size())
        .setAttribute("firstBlockSize", firstBlock.size())
        .setAttribute("previousRefCount", previousRefs.size())
        .setAttribute("newRefCount", newRefs.size())
        .setAttribute("unsafeBlockSize", unsafeBlockSize)
        .setAttribute("greenIndex", newCommitsAndSavedGreenIndex.first)
        .setAttribute("redIndex", redCommitsAndSavedRedIndex.first)
        .setAttribute("newCommitCount", allNewsCommits.size())
        .setAttribute("removedCommitCount", removeCommits.size());

      List<Commit> unsafePartSavedLog = buildUnsafePartSavedLog(savedLog, unsafeBlockSize, removeCommits, firstBlock);

      unsafePartSavedLog = new NewCommitIntegrator<>(unsafePartSavedLog, allNewsCommits).getResultList();

      return Pair.create(ContainerUtil.concat(unsafePartSavedLog, savedLog.subList(unsafeBlockSize, savedLog.size())),
                         unsafePartSavedLog.size() - unsafeBlockSize);
    }
    finally {
      stopWatch.report(LOG);
    }
  }

  /**
   * Builds the "unsafe" prefix of the log to be rebuilt: drops removed (red) commits from {@code savedLog},
   * and for any commit that was re-fetched in {@code firstBlock}, replaces the stale saved copy with the
   * up-to-date one (so its current parents, not the parents from the previous refresh, are used downstream).
   */
  private @NotNull List<Commit> buildUnsafePartSavedLog(@NotNull List<? extends Commit> savedLog,
                                                         int unsafeBlockSize,
                                                         @NotNull Set<CommitId> removeCommits,
                                                         @NotNull List<? extends Commit> firstBlock) {
    StopWatch stopWatch = StopWatch.start("VcsLogJoiner.buildUnsafePartSavedLog");
    try {
      Map<CommitId, Commit> recentCommitsById = new HashMap<>();
      for (Commit commit : firstBlock) {
        recentCommitsById.put(commit.getId(), commit);
      }

      List<Commit> unsafePartSavedLog = new ArrayList<>();
      for (Commit commit : savedLog.subList(0, unsafeBlockSize)) {
        if (!removeCommits.contains(commit.getId())) {
          unsafePartSavedLog.add(recentCommitsById.getOrDefault(commit.getId(), commit));
        }
      }
      return unsafePartSavedLog;
    }
    finally {
      stopWatch.report(LOG);
    }
  }


  private @NotNull Pair<Integer, Set<Commit>> getNewCommitsAndSavedGreenIndex(@NotNull List<? extends Commit> savedLog,
                                                                              @NotNull Collection<? extends CommitId> previousRefs,
                                                                              @NotNull List<? extends Commit> firstBlock,
                                                                              @NotNull Collection<? extends CommitId> newRefs) {
    StopWatch stopWatch = StopWatch.start("VcsLogJoiner.getNewCommitsAndSavedGreenIndex");
    try {
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
    finally {
      stopWatch.report(LOG);
    }
  }

  private int getFirstUnTrackedIndex(@NotNull List<? extends Commit> commits, @NotNull Set<CommitId> searchHashes) {
    StopWatch stopWatch = StopWatch.start("VcsLogJoiner.getFirstUnTrackedIndex");
    try {
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
    finally {
      stopWatch.report(LOG);
    }
  }

  private Set<Commit> getAllNewCommits(@NotNull List<? extends Commit> unsafeGreenPartSavedLog,
                                       @NotNull List<? extends Commit> firstBlock) {
    StopWatch stopWatch = StopWatch.start("VcsLogJoiner.getAllNewCommits");
    try {
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
    finally {
      stopWatch.report(LOG);
    }
  }

  private @NotNull Pair<Integer, Set<CommitId>> getRedCommitsAndSavedRedIndex(@NotNull List<? extends Commit> savedLog,
                                                                              @NotNull Collection<? extends CommitId> previousRefs,
                                                                              @NotNull List<? extends Commit> firstBlock,
                                                                              @NotNull Collection<? extends CommitId> newRefs) {
    StopWatch stopWatch = StopWatch.start("VcsLogJoiner.getRedCommitsAndSavedRedIndex");
    try {
      Set<CommitId> startRedCommits = new HashSet<>(previousRefs);
      startRedCommits.removeAll(newRefs);
      Set<CommitId> startGreenNodes = new HashSet<>(newRefs);
      Map<CommitId, List<CommitId>> recentCommitParents = new HashMap<>();
      for (Commit commit : firstBlock) {
        recentCommitParents.put(commit.getId(), commit.getParents());
        startGreenNodes.add(commit.getId());
        startGreenNodes.addAll(commit.getParents());
      }
      RedGreenSorter<CommitId, Commit> sorter = new RedGreenSorter<>(startRedCommits, startGreenNodes, savedLog, recentCommitParents);
      int saveRegIndex = sorter.getFirstSaveIndex();

      return new Pair<>(saveRegIndex, sorter.getAllRedCommit());
    }
    finally {
      stopWatch.report(LOG);
    }
  }

  private static final class RedGreenSorter<CommitId, Commit extends GraphCommit<CommitId>> {
    private final Set<? super CommitId> currentRed;
    private final Set<? super CommitId> currentGreen;
    private final Set<CommitId> allRedCommit = new HashSet<>();

    private final List<? extends Commit> savedLog;
    private final Map<CommitId, List<CommitId>> recentCommitParents;

    private RedGreenSorter(Set<? super CommitId> startRedNodes, Set<? super CommitId> startGreenNodes, List<? extends Commit> savedLog,
                           Map<CommitId, List<CommitId>> recentCommitParents) {
      this.currentRed = startRedNodes;
      this.currentGreen = startGreenNodes;
      this.savedLog = savedLog;
      this.recentCommitParents = recentCommitParents;
    }

    private void markRealRedNode(@NotNull CommitId node) {
      if (!currentRed.remove(node)) {
        throw new IllegalStateException(ILLEGAL_DATA_RELOAD_ALL); // see VcsLogJoinerTest#illegalStateExceptionTest2
      }
      allRedCommit.add(node);
    }

    private int getFirstSaveIndex() {
      StopWatch stopWatch = StopWatch.start("VcsLogJoiner.RedGreenSorter.getFirstSaveIndex");
      try {
        for (int lastIndex = 0; lastIndex < savedLog.size(); lastIndex++) {
          Commit commit = savedLog.get(lastIndex);
          boolean isGreen = currentGreen.contains(commit.getId());
          if (isGreen) {
            currentRed.remove(commit.getId());
            // For a commit that was re-fetched in firstBlock, its updated parents are already
            // in currentGreen (seeded when the sorter was built). Propagating its parents from
            // savedLog here would incorrectly resurrect parents that were dropped in the update;
            // instead, only such dropped parents become (tentative) red candidates.
            // updatedParents can legitimately be empty (e.g. the commit was re-fetched as an orphan/root) -
            // in that case every old parent counts as dropped and becomes a red candidate below.
            List<CommitId> updatedParents = recentCommitParents.get(commit.getId());
            if (updatedParents == null) {
              currentGreen.addAll(commit.getParents());
            } else {
              Set<CommitId> droppedParents = new HashSet<>(commit.getParents());
              droppedParents.removeAll(new HashSet<>(updatedParents));
              currentRed.addAll(droppedParents);
            }
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
      finally {
        stopWatch.report(LOG);
      }
    }

    public Set<CommitId> getAllRedCommit() {
      return allRedCommit;
    }
  }


  /**
   * Puts each new commit into {@code list}, before the first commit of {@code list} that is its parent
   * or that is older than it. A commit always comes before its parents.
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public static class NewCommitIntegrator<CommitId, Commit extends GraphCommit<CommitId>> {
    private static final @NonNls String INDEXED_INSERT_KEY = "vcs.log.joiner.indexed.insert";

    private final List<Commit> list;
    private final Map<CommitId, Commit> newCommitsMap;

    private final Stack<Commit> commitsStack;

    /**
     * The new commits to put before {@code list.get(index)}, by that index. An index of {@code list.size()} means the end of the list.
     */
    private final TreeMap<Integer, List<Commit>> insertedByIndex = new TreeMap<>();
    /**
     * The smallest timestamp of {@code list.get(0)} up to {@code list.get(index)}, by that index.
     * It never grows, so a binary search finds the first commit of {@code list} that is older than a new commit.
     */
    private long[] prefixMinTimestamp;
    /**
     * The index in {@code list} of each commit that is a parent of a new commit.
     */
    private Map<CommitId, Integer> parentIndexes;

    @VisibleForTesting
    @ApiStatus.Internal
    public NewCommitIntegrator(@NotNull List<Commit> list, @NotNull Collection<Commit> newCommits) {
      this.list = list;
      newCommitsMap = new HashMap<>();
      for (Commit commit : newCommits) {
        newCommitsMap.put(commit.getId(), commit);
      }
      commitsStack = new Stack<>();
    }

    private void insertAllUseStack(@NotNull Consumer<? super Commit> insert) {
      StopWatch stopWatch = StopWatch.start("VcsLogJoiner.NewCommitIntegrator.insertAllUseStack");
      try {
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

            insert.accept(currentCommit);
            commitsStack.pop();
          }
        }
      }
      finally {
        stopWatch.report(LOG);
      }
    }

    /**
     * Reads {@code list} from the start to find the place of {@code commit}.
     */
    private void insertScanning(@NotNull Commit commit) {
      int insertIndex;
      Set<CommitId> parents = new HashSet<>(commit.getParents());
      for (insertIndex = 0; insertIndex < list.size(); insertIndex++) {
        Commit someCommit = list.get(insertIndex);
        if (parents.contains(someCommit.getId())) {
          break;
        }
        if (someCommit.getTimestamp() < commit.getTimestamp()) {
          break;
        }
      }

      list.add(insertIndex, commit);
    }

    /**
     * Fills {@link #prefixMinTimestamp} and {@link #parentIndexes}, so that {@link #insertIndexed} does not read all of {@code list}.
     */
    private void prepareIndexes() {
      Set<CommitId> parentIds = new HashSet<>();
      for (Commit commit : newCommitsMap.values()) {
        parentIds.addAll(commit.getParents());
      }

      prefixMinTimestamp = new long[list.size()];
      parentIndexes = new HashMap<>();
      long minTimestamp = Long.MAX_VALUE;
      for (int index = 0; index < list.size(); index++) {
        Commit commit = list.get(index);
        minTimestamp = Math.min(minTimestamp, commit.getTimestamp());
        prefixMinTimestamp[index] = minTimestamp;
        if (parentIds.contains(commit.getId())) {
          parentIndexes.put(commit.getId(), index);
        }
      }
    }

    /**
     * Finds the place of {@code commit} with {@link #prefixMinTimestamp} and {@link #parentIndexes},
     * and keeps it in {@link #insertedByIndex}. It gives the same result as {@link #insertScanning}.
     */
    private void insertIndexed(@NotNull Commit commit) {
      Set<CommitId> parents = new HashSet<>(commit.getParents());
      int index = firstOlderIndex(commit.getTimestamp());
      for (CommitId parent : parents) {
        Integer parentIndex = parentIndexes.get(parent);
        if (parentIndex != null && parentIndex < index) {
          index = parentIndex;
        }
      }

      // the new commits before list.get(index) come first, so the place of the commit can be between them
      for (List<Commit> inserted : insertedByIndex.headMap(index, true).values()) {
        for (int position = 0; position < inserted.size(); position++) {
          if (goesBefore(commit, parents, inserted.get(position))) {
            inserted.add(position, commit);
            return;
          }
        }
      }
      insertedByIndex.computeIfAbsent(index, __ -> new ArrayList<>()).add(commit);
    }

    /**
     * Returns {@code true} if {@code commit} must come before {@code someCommit}, because {@code someCommit} is its parent or is older.
     * {@code parents} are the parents of {@code commit}.
     */
    private boolean goesBefore(@NotNull Commit commit, @NotNull Set<CommitId> parents, @NotNull Commit someCommit) {
      return someCommit.getTimestamp() < commit.getTimestamp() || parents.contains(someCommit.getId());
    }

    /**
     * Returns the index of the first commit of {@code list} that is older than {@code timestamp}, or the size of {@code list}.
     */
    private int firstOlderIndex(long timestamp) {
      int low = 0;
      int high = prefixMinTimestamp.length;
      while (low < high) {
        int middle = (low + high) >>> 1;
        if (prefixMinTimestamp[middle] < timestamp) {
          high = middle;
        }
        else {
          low = middle + 1;
        }
      }
      return low;
    }

    private @NotNull List<Commit> buildResultList() {
      int insertedCount = 0;
      for (List<Commit> inserted : insertedByIndex.values()) {
        insertedCount += inserted.size();
      }

      List<Commit> result = new ArrayList<>(list.size() + insertedCount);
      int from = 0;
      for (Map.Entry<Integer, List<Commit>> entry : insertedByIndex.entrySet()) {
        result.addAll(list.subList(from, entry.getKey()));
        result.addAll(entry.getValue());
        from = entry.getKey();
      }
      result.addAll(list.subList(from, list.size()));
      return result;
    }

    private void visitCommit(@NotNull Commit commit) {
      commitsStack.push(commit);
      newCommitsMap.remove(commit.getId());
    }

    public @NotNull List<Commit> getResultList() {
      return getResultList(Registry.is(INDEXED_INSERT_KEY, true));
    }

    @VisibleForTesting
    public @NotNull List<Commit> getResultList(boolean indexedInsert) {
      if (newCommitsMap.isEmpty()) {
        return list;
      }
      if (!indexedInsert) {
        insertAllUseStack(this::insertScanning);
        return list;
      }

      prepareIndexes();
      insertAllUseStack(this::insertIndexed);
      return buildResultList();
    }
  }
}
