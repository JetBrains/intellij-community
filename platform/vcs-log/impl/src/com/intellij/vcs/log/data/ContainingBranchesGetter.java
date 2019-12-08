// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Provides capabilities to asynchronously calculate "contained in branches" information.
 */
public class ContainingBranchesGetter {
  private static final Logger LOG = Logger.getInstance(ContainingBranchesGetter.class);

  @NotNull private final SequentialLimitedLifoExecutor<Task> myTaskExecutor;
  @NotNull private final VcsLogData myLogData;

  // other fields accessed only from EDT
  @NotNull private final List<Runnable> myLoadingFinishedListeners = new ArrayList<>();
  @NotNull private SLRUMap<CommitId, List<String>> myCache = createCache();
  @NotNull private final CurrentBranchConditionCache myConditionsCache;
  private int myCurrentBranchesChecksum;

  ContainingBranchesGetter(@NotNull VcsLogData logData, @NotNull Disposable parentDisposable) {
    myLogData = logData;
    myConditionsCache = new CurrentBranchConditionCache(logData, parentDisposable);
    myTaskExecutor = new SequentialLimitedLifoExecutor<>(parentDisposable, 10, task -> {
      final List<String> branches = task.getContainingBranches(myLogData);
      ApplicationManager.getApplication().invokeLater(() -> {
        // if cache is cleared (because of log refresh) during this task execution,
        // this will put obsolete value into the old instance we don't care anymore
        task.cache.put(new CommitId(task.hash, task.root), branches);
        notifyListeners();
      });
    });
    myLogData.addDataPackChangeListener(dataPack -> {
      Collection<VcsRef> currentBranches = dataPack.getRefsModel().getBranches();
      int checksum = currentBranches.hashCode();
      if (myCurrentBranchesChecksum != 0 && myCurrentBranchesChecksum != checksum) { // clear cache if branches set changed after refresh
        clearCache();
      }
      myCurrentBranchesChecksum = checksum;
    });
  }

  private void clearCache() {
    myCache = createCache();
    myTaskExecutor.clear();
    myConditionsCache.clear();
    // re-request containing branches information for the commit user (possibly) currently stays on
    ApplicationManager.getApplication().invokeLater(this::notifyListeners);
  }

  /**
   * This task will be executed each time the calculating process completes.
   */
  public void addTaskCompletedListener(@NotNull Runnable runnable) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    myLoadingFinishedListeners.add(runnable);
  }

  public void removeTaskCompletedListener(@NotNull Runnable runnable) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    myLoadingFinishedListeners.remove(runnable);
  }

  private void notifyListeners() {
    LOG.assertTrue(EventQueue.isDispatchThread());
    for (Runnable listener : myLoadingFinishedListeners) {
      listener.run();
    }
  }

  /**
   * Returns the alphabetically sorted list of branches containing the specified node, if this information is ready;
   * if it is not available, starts calculating in the background and returns null.
   */
  @Nullable
  public List<String> requestContainingBranches(@NotNull VirtualFile root, @NotNull Hash hash) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    List<String> refs = getContainingBranchesFromCache(root, hash);
    if (refs == null) {
      DataPack dataPack = myLogData.getDataPack();
      myTaskExecutor.queue(new Task(root, hash, myCache, dataPack.getPermanentGraph(), dataPack.getRefsModel()));
    }
    return refs;
  }

  @Nullable
  public List<String> getContainingBranchesFromCache(@NotNull VirtualFile root, @NotNull Hash hash) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    return myCache.get(new CommitId(hash, root));
  }

  @Nullable
  public List<String> getContainingBranchesQuickly(@NotNull VirtualFile root, @NotNull Hash hash) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    CommitId commitId = new CommitId(hash, root);
    List<String> branches = myCache.get(commitId);
    if (branches == null) {
      int index = myLogData.getCommitIndex(hash, root);
      PermanentGraph<Integer> pg = myLogData.getDataPack().getPermanentGraph();
      if (pg instanceof PermanentGraphInfo) {
        //noinspection unchecked
        int nodeId = ((PermanentGraphInfo)pg).getPermanentCommitsInfo().getNodeId(index);
        if (nodeId < 10000 && canUseGraphForComputation(myLogData.getLogProvider(root))) {
          branches = getContainingBranchesSynchronously(root, hash);
        }
        else {
          branches = BackgroundTaskUtil.tryComputeFast(indicator -> getContainingBranchesSynchronously(root, hash), 100);
        }
        if (branches != null) myCache.put(commitId, branches);
      }
    }
    return branches;
  }

  @CalledInAny
  @NotNull
  public Condition<Integer> getContainedInCurrentBranchCondition(@NotNull VirtualFile root) {
    return myConditionsCache.getContainedInCurrentBranchCondition(root);
  }

  @NotNull
  private static SLRUMap<CommitId, List<String>> createCache() {
    return new SLRUMap<>(1000, 1000);
  }

  @CalledInAny
  @NotNull
  public List<String> getContainingBranchesSynchronously(@NotNull VirtualFile root, @NotNull Hash hash) {
    return doGetContainingBranches(myLogData.getDataPack(), root, hash);
  }

  @NotNull
  private List<String> doGetContainingBranches(@NotNull DataPack dataPack, @NotNull VirtualFile root, @NotNull Hash hash) {
    return new Task(root, hash, myCache, dataPack.getPermanentGraph(), dataPack.getRefsModel()).getContainingBranches(myLogData);
  }

  private static boolean canUseGraphForComputation(@NotNull VcsLogProvider logProvider) {
    return VcsLogProperties.LIGHTWEIGHT_BRANCHES.getOrDefault(logProvider);
  }

  private static class Task {
    @NotNull private final VirtualFile root;
    @NotNull private final Hash hash;
    @NotNull private final SLRUMap<CommitId, List<String>> cache;
    @NotNull private final RefsModel refs;
    @NotNull private final PermanentGraph<Integer> graph;

    Task(@NotNull VirtualFile root,
         @NotNull Hash hash,
         @NotNull SLRUMap<CommitId, List<String>> cache,
         @NotNull PermanentGraph<Integer> graph,
         @NotNull RefsModel refs) {
      this.root = root;
      this.hash = hash;
      this.cache = cache;
      this.graph = graph;
      this.refs = refs;
    }

    @NotNull
    public List<String> getContainingBranches(@NotNull VcsLogData logData) {
      StopWatch sw = StopWatch.start("get containing branches");
      try {
        VcsLogProvider provider = logData.getLogProvider(root);
        if (canUseGraphForComputation(provider)) {
          Set<Integer> branchesIndexes = graph.getContainingBranches(logData.getCommitIndex(hash, root));

          Collection<VcsRef> branchesRefs = new HashSet<>();
          for (Integer index : branchesIndexes) {
            refs.refsToCommit(index).stream().filter(ref -> ref.getType().isBranch()).forEach(branchesRefs::add);
          }
          branchesRefs = ContainerUtil.sorted(branchesRefs, provider.getReferenceManager().getLabelsOrderComparator());

          ArrayList<String> branchesList = new ArrayList<>();
          for (VcsRef ref : branchesRefs) {
            branchesList.add(ref.getName());
          }
          return branchesList;
        }
        else {
          List<String> branches = new ArrayList<>(provider.getContainingBranches(root, hash));
          Collections.sort(branches);
          return branches;
        }
      }
      catch (VcsException e) {
        LOG.warn(e);
        return Collections.emptyList();
      }
      finally {
        sw.report();
      }
    }
  }
}
