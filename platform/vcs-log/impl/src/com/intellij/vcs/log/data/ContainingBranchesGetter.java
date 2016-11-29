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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Provides capabilities to asynchronously calculate "contained in branches" information.
 */
public class ContainingBranchesGetter {

  private static final Logger LOG = Logger.getInstance(ContainingBranchesGetter.class);

  @NotNull private final SequentialLimitedLifoExecutor<Task> myTaskExecutor;
  @NotNull private final VcsLogData myLogData;

  // other fields accessed only from EDT
  @NotNull private final List<Runnable> myLoadingFinishedListeners = ContainerUtil.newArrayList();
  @NotNull private SLRUMap<CommitId, List<String>> myCache = createCache();
  @NotNull private Map<VirtualFile, ContainedInBranchCondition> myConditions = ContainerUtil.newHashMap();
  private int myCurrentBranchesChecksum;

  ContainingBranchesGetter(@NotNull VcsLogData logData, @NotNull Disposable parentDisposable) {
    myLogData = logData;
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
    Map<VirtualFile, ContainedInBranchCondition> conditions = myConditions;
    myConditions = ContainerUtil.newHashMap();
    for (ContainedInBranchCondition c : conditions.values()) {
      c.dispose();
    }
    // re-request containing branches information for the commit user (possibly) currently stays on
    ApplicationManager.getApplication().invokeLater(() -> notifyListeners());
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
    List<String> refs = myCache.get(new CommitId(hash, root));
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

  @NotNull
  public Condition<CommitId> getContainedInBranchCondition(@NotNull final String branchName, @NotNull final VirtualFile root) {
    LOG.assertTrue(EventQueue.isDispatchThread());

    DataPack dataPack = myLogData.getDataPack();
    if (dataPack == DataPack.EMPTY) return Conditions.alwaysFalse();

    PermanentGraph<Integer> graph = dataPack.getPermanentGraph();
    VcsLogRefs refs = dataPack.getRefsModel();

    ContainedInBranchCondition condition = myConditions.get(root);
    if (condition == null || !condition.getBranch().equals(branchName)) {
      VcsRef branchRef = ContainerUtil.find(refs.getBranches(),
                                            vcsRef -> vcsRef.getRoot().equals(root) && vcsRef.getName().equals(branchName));
      if (branchRef == null) return Conditions.alwaysFalse();
      condition = new ContainedInBranchCondition(graph.getContainedInBranchCondition(
        Collections.singleton(myLogData.getCommitIndex(branchRef.getCommitHash(), branchRef.getRoot()))), branchName);
      myConditions.put(root, condition);
    }
    return condition;
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

  private static class Task {
    private final VirtualFile root;
    private final Hash hash;
    private final SLRUMap<CommitId, List<String>> cache;
    @Nullable private final RefsModel refs;
    @Nullable private final PermanentGraph<Integer> graph;

    public Task(VirtualFile root,
                Hash hash,
                SLRUMap<CommitId, List<String>> cache,
                @Nullable PermanentGraph<Integer> graph,
                @Nullable RefsModel refs) {
      this.root = root;
      this.hash = hash;
      this.cache = cache;
      this.graph = graph;
      this.refs = refs;
    }

    @NotNull
    public List<String> getContainingBranches(@NotNull VcsLogData logData) {
      try {
        VcsLogProvider provider = logData.getLogProvider(root);
        if (graph != null && refs != null && VcsLogProperties.get(provider, VcsLogProperties.LIGHTWEIGHT_BRANCHES)) {
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
    }
  }

  private class ContainedInBranchCondition implements Condition<CommitId> {
    @NotNull private final Condition<Integer> myCondition;
    @NotNull private final String myBranch;
    private volatile boolean isDisposed = false;

    public ContainedInBranchCondition(@NotNull Condition<Integer> condition, @NotNull String branch) {
      myCondition = condition;
      myBranch = branch;
    }

    @NotNull
    public String getBranch() {
      return myBranch;
    }

    @Override
    public boolean value(CommitId commitId) {
      if (isDisposed) return false;
      return myCondition.value(myLogData.getCommitIndex(commitId.getHash(), commitId.getRoot()));
    }

    public void dispose() {
      isDisposed = true;
    }
  }
}
