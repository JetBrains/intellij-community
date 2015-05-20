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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Provides capabilities to asynchronously calculate "contained in branches" information.
 */
public class ContainingBranchesGetter implements VcsLogListener {

  private static final Logger LOG = Logger.getInstance(ContainingBranchesGetter.class);

  @NotNull private final SequentialLimitedLifoExecutor<Task> myTaskExecutor;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private volatile SLRUMap<Hash, List<String>> myCache = createCache();
  @Nullable private Runnable myLoadingFinishedListener; // access only from EDT
  private int myCurrentBranchesChecksum;
  @Nullable private VcsLogRefs myRefs;
  @Nullable private PermanentGraph<Integer> myGraph;

  ContainingBranchesGetter(@NotNull VcsLogDataHolder dataHolder, @NotNull Disposable parentDisposable) {
    myDataHolder = dataHolder;
    myTaskExecutor = new SequentialLimitedLifoExecutor<Task>(parentDisposable, 10, new ThrowableConsumer<Task, Throwable>() {
      @Override
      public void consume(final Task task) throws Throwable {
        final List<String> branches = task.getContainingBranches(myDataHolder);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            // if cache is cleared (because of log refresh) during this task execution,
            // this will put obsolete value into the old instance we don't care anymore
            task.cache.put(task.hash, branches);
            notifyListener();
          }
        });
      }
    });
  }

  @Override
  public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
    if (refreshHappened) {
      myRefs = dataPack.getRefs();
      Collection<VcsRef> currentBranches = myRefs.getBranches();
      int checksum = currentBranches.hashCode();
      if (myCurrentBranchesChecksum != 0 && myCurrentBranchesChecksum != checksum) { // clear cache if branches set changed after refresh
        clearCache();
      }
      myCurrentBranchesChecksum = checksum;
      myGraph = dataPack.getPermanentGraph();
    }
  }

  private void clearCache() {
    myCache = createCache();
    myTaskExecutor.clear();
    // re-request containing branches information for the commit user (possibly) currently stays on
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        notifyListener();
      }
    });
  }

  /**
   * This task will be executed each time the calculating process completes.
   */
  public void setTaskCompletedListener(@NotNull Runnable runnable) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    myLoadingFinishedListener = runnable;
  }

  private void notifyListener() {
    LOG.assertTrue(EventQueue.isDispatchThread());
    if (myLoadingFinishedListener != null) {
      myLoadingFinishedListener.run();
    }
  }

  /**
   * Returns the alphabetically sorted list of branches containing the specified node, if this information is ready;
   * if it is not available, starts calculating in the background and returns null.
   */
  @Nullable
  public List<String> requestContainingBranches(@NotNull VirtualFile root, @NotNull Hash hash) {
    List<String> refs = myCache.get(hash);
    if (refs == null) {
      myTaskExecutor.queue(new Task(root, hash, myCache, myGraph, myRefs));
    }
    return refs;
  }

  @NotNull
  private static SLRUMap<Hash, List<String>> createCache() {
    return new SLRUMap<Hash, List<String>>(1000, 1000);
  }

  private static class Task {
    private final VirtualFile root;
    private final Hash hash;
    private final SLRUMap<Hash, List<String>> cache;
    @Nullable private final VcsLogRefs refs;
    @Nullable private final PermanentGraph<Integer> graph;

    public Task(VirtualFile root,
                Hash hash,
                SLRUMap<Hash, List<String>> cache,
                @Nullable PermanentGraph<Integer> graph,
                @Nullable VcsLogRefs refs) {
      this.root = root;
      this.hash = hash;
      this.cache = cache;
      this.graph = graph;
      this.refs = refs;
    }

    @NotNull
    public List<String> getContainingBranches(VcsLogDataHolder dataHolder) {
      try {
        VcsLogProvider provider = dataHolder.getLogProvider(root);
        if (graph != null && refs != null && VcsLogProperties.get(provider, VcsLogProperties.LIGHTWEIGHT_BRANCHES)) {
          Set<Integer> branchesIndexes = graph.getContainingBranches(dataHolder.getCommitIndex(hash));

          Collection<VcsRef> branchesRefs = new HashSet<VcsRef>();
          for (Integer index : branchesIndexes) {
            branchesRefs.addAll(refs.refsToCommit(index));
          }
          branchesRefs = ContainerUtil.sorted(branchesRefs, provider.getReferenceManager().getLabelsOrderComparator());

          ArrayList<String> branchesList = new ArrayList<String>();
          for (VcsRef ref : branchesRefs) {
            if (ref.getType().isBranch()) {
              branchesList.add(ref.getName());
            }
          }
          return branchesList;
        }
        else {
          List<String> branches = new ArrayList<String>(provider.getContainingBranches(root, hash));
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

}
