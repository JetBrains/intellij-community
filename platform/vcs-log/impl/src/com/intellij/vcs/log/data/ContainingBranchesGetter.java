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

  @NotNull private final SequentialLimitedLifoExecutor<CachingTask> myTaskExecutor;
  @NotNull private final VcsLogData myLogData;

  // other fields accessed only from EDT
  @NotNull private final List<Runnable> myLoadingFinishedListeners = new ArrayList<>();
  @NotNull private SLRUMap<CommitId, List<String>> myCache = createCache();
  @NotNull private final CurrentBranchConditionCache myConditionsCache;
  private int myCurrentBranchesChecksum;

  ContainingBranchesGetter(@NotNull VcsLogData logData, @NotNull Disposable parentDisposable) {
    myLogData = logData;
    myConditionsCache = new CurrentBranchConditionCache(logData, parentDisposable);
    myTaskExecutor = new SequentialLimitedLifoExecutor<>(parentDisposable, 10, CachingTask::run);
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
      myTaskExecutor.queue(new CachingTask(createTask(root, hash, myLogData.getDataPack()), myCache));
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
    return createTask(root, hash, myLogData.getDataPack()).getContainingBranches();
  }

  @NotNull
  private ContainingBranchesGetter.Task createTask(@NotNull VirtualFile root, @NotNull Hash hash, @NotNull DataPack dataPack) {
    VcsLogProvider provider = myLogData.getLogProvider(root);
    if (canUseGraphForComputation(provider)) {
      return new GraphTask(provider, root, hash, dataPack);
    }
    return new ProviderTask(provider, root, hash);
  }

  private static boolean canUseGraphForComputation(@NotNull VcsLogProvider logProvider) {
    return VcsLogProperties.LIGHTWEIGHT_BRANCHES.getOrDefault(logProvider);
  }

  private abstract static class Task {
    @NotNull private final VcsLogProvider myProvider;
    @NotNull private final VirtualFile myRoot;
    @NotNull private final Hash myHash;

    Task(@NotNull VcsLogProvider provider, @NotNull VirtualFile root, @NotNull Hash hash) {
      myProvider = provider;
      myRoot = root;
      myHash = hash;
    }

    @NotNull
    public List<String> getContainingBranches() {
      StopWatch sw = StopWatch.start("get containing branches");
      try {
        return getContainingBranches(myProvider, myRoot, myHash);
      }
      catch (VcsException e) {
        LOG.warn(e);
        return Collections.emptyList();
      }
      finally {
        sw.report();
      }
    }


    protected abstract @NotNull List<String> getContainingBranches(@NotNull VcsLogProvider provider,
                                                                   @NotNull VirtualFile root, @NotNull Hash hash) throws VcsException;
  }

  private class GraphTask extends Task {
    @NotNull private final RefsModel myRefs;
    @NotNull private final PermanentGraph<Integer> myGraph;

    GraphTask(@NotNull VcsLogProvider provider, @NotNull VirtualFile root, @NotNull Hash hash, @NotNull DataPack dataPack) {
      super(provider, root, hash);
      myGraph = dataPack.getPermanentGraph();
      myRefs = dataPack.getRefsModel();
    }

    @Override
    protected @NotNull List<String> getContainingBranches(@NotNull VcsLogProvider provider, @NotNull VirtualFile root, @NotNull Hash hash) {
      Set<Integer> branchesIndexes = myGraph.getContainingBranches(myLogData.getCommitIndex(hash, root));

      Collection<VcsRef> branchesRefs = new HashSet<>();
      for (Integer index : branchesIndexes) {
        myRefs.refsToCommit(index).stream().filter(ref -> ref.getType().isBranch()).forEach(branchesRefs::add);
      }
      branchesRefs = ContainerUtil.sorted(branchesRefs, provider.getReferenceManager().getLabelsOrderComparator());

      ArrayList<String> branchesList = new ArrayList<>();
      for (VcsRef ref : branchesRefs) {
        branchesList.add(ref.getName());
      }
      return branchesList;
    }
  }

  private static class ProviderTask extends Task {

    ProviderTask(@NotNull VcsLogProvider provider, @NotNull VirtualFile root, @NotNull Hash hash) {
      super(provider, root, hash);
    }

    @Override
    public @NotNull List<String> getContainingBranches(@NotNull VcsLogProvider provider,
                                                       @NotNull VirtualFile root, @NotNull Hash hash) throws VcsException {
      List<String> branches = new ArrayList<>(provider.getContainingBranches(root, hash));
      Collections.sort(branches);
      return branches;
    }
  }

  private class CachingTask {
    @NotNull private final Task myTask;
    @NotNull private final SLRUMap<CommitId, List<String>> myCache;

    CachingTask(@NotNull Task task, @NotNull SLRUMap<CommitId, List<String>> cache) {
      myTask = task;
      myCache = cache;
    }

    public void run() {
      List<String> branches = myTask.getContainingBranches();
      ApplicationManager.getApplication().invokeLater(() -> {
        // if cache is cleared (because of log refresh) during this task execution,
        // this will put obsolete value into the old instance we don't care anymore
        myCache.put(new CommitId(myTask.myHash, myTask.myRoot), branches);
        notifyListeners();
      });
    }
  }
}
