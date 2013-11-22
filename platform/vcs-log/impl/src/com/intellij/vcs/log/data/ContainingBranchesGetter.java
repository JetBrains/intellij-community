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
import com.intellij.openapi.util.Condition;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Provides capabilities to asynchronously calculate "contained in branches" information.
 */
public class ContainingBranchesGetter {

  @NotNull private final SequentialLimitedLifoExecutor<Task> myTaskExecutor;
  @NotNull private final Collection<Runnable> myLoadingFinishedListeners = new ArrayList<Runnable>();
  @NotNull private final SLRUMap<Node, List<VcsRef>> myCache = new SLRUMap<Node, List<VcsRef>>(1000, 1000);

  ContainingBranchesGetter(@NotNull Disposable parentDisposable) {
    myTaskExecutor = new SequentialLimitedLifoExecutor<Task>(parentDisposable, 10, new ThrowableConsumer<Task, Throwable>() {
      @Override
      public void consume(Task task) throws Throwable {
        myCache.put(task.node, getContainingBranches(task.pack, task.node));
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            for (Runnable listener : myLoadingFinishedListeners) {
              listener.run();
            }
          }
        });
      }
    });
  }

  void clearCache() {
    myCache.clear();
  }

  /**
   * This task will be executed each time the calculating process completes.
   */
  public void addTaskCompletedListener(@NotNull Runnable runnable) {
    myLoadingFinishedListeners.add(runnable);
  }

  /**
   * Returns the alphabetically sorted list of branches containing the specified node, if this information is ready;
   * if it is not available, starts calculating in the background and returns null.
   */
  @Nullable
  public List<VcsRef> requestContainingBranches(@NotNull DataPack dataPack, @NotNull Node node) {
    List<VcsRef> refs = myCache.get(node);
    if (refs == null) {
      myTaskExecutor.queue(new Task(dataPack, node));
    }
    return refs;
  }

  @NotNull
  private List<VcsRef> getContainingBranches(@NotNull DataPack dataPack, @NotNull Node node) {
    RefsModel refsModel = dataPack.getRefsModel();
    Set<VcsRef> containingBranches = ContainerUtil.newHashSet();

    Set<Node> visitedNodes = ContainerUtil.newHashSet();
    Set<Node> nodesToCheck = ContainerUtil.newHashSet();
    nodesToCheck.add(node);
    while (!nodesToCheck.isEmpty()) {
      Iterator<Node> nodeIterator = nodesToCheck.iterator();
      Node nextNode = nodeIterator.next();
      nodeIterator.remove();

      if (!visitedNodes.add(nextNode)) {
        continue;
      }

      for (Edge edge : nextNode.getUpEdges()) {
        Node upNode = edge.getUpNode();
        // optimization: the node is contained in all branches which contain its child => no need to walk over this graph branch
        List<VcsRef> upRefs = myCache.get(upNode);
        if (upRefs != null) {
          containingBranches.addAll(upRefs);
        }
        else {
          nodesToCheck.add(upNode);
        }
      }

      containingBranches.addAll(getBranchesPointingToThisNode(refsModel, nextNode));
    }

    return sortByName(containingBranches);
  }

  @NotNull
  private static Collection<VcsRef> getBranchesPointingToThisNode(@NotNull RefsModel refsModel, @NotNull Node node) {
    return ContainerUtil.filter(refsModel.refsToCommit(node.getCommitIndex()), new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });
  }

  private static List<VcsRef> sortByName(Collection<VcsRef> branches) {
    List<VcsRef> branchesList = new ArrayList<VcsRef>(branches);
    ContainerUtil.sort(branchesList, new Comparator<VcsRef>() {
      @Override
      public int compare(VcsRef o1, VcsRef o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return branchesList;
  }

  private static class Task {
    private final DataPack pack;
    private final Node node;
    public Task(DataPack pack, Node node) {
      this.pack = pack;
      this.node = node;
    }
  }

}
