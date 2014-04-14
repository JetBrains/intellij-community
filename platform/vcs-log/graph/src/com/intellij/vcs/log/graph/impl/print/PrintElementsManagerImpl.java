/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.impl.print;

import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.impl.visible.FragmentGenerator;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class PrintElementsManagerImpl<CommitId> extends AbstractPrintElementsManager<CommitId> {

  @NotNull
  private final FragmentGenerator myFragmentGenerator;

  @NotNull
  private final DfsUtil myDfsUtil = new DfsUtil();

  public PrintElementsManagerImpl(@NotNull LinearGraphWithCommitInfo<CommitId> printedLinearGraph,
                                  @NotNull FragmentGenerator fragmentGenerator,
                                  @NotNull GraphColorManager<CommitId> colorManager) {
    super(printedLinearGraph, colorManager);
    myFragmentGenerator = fragmentGenerator;
  }

  @SuppressWarnings("unused") // for future
  private void enableAllRelativeNodes(@NotNull final Flags flags, final int rowIndex) {
    flags.set(rowIndex, true);

    myDfsUtil.nodeDfsIterator(rowIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int downNode : myPrintedLinearGraph.getDownNodes(currentNode)) {
          if (downNode != LinearGraph.NOT_LOAD_COMMIT && !flags.get(downNode)) {
            flags.set(downNode, true);
            return downNode;
          }
        }
        return DfsUtil.NextNode.NODE_NOT_FOUND;
      }
    });

    myDfsUtil.nodeDfsIterator(rowIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int upNode : myPrintedLinearGraph.getUpNodes(currentNode)) {
          if (!flags.get(upNode)) {
            flags.set(upNode, true);
            return upNode;
          }
        }
        return DfsUtil.NextNode.NODE_NOT_FOUND;
      }
    });
  }

  @NotNull
  protected Set<Integer> getSelectedNodes(@NotNull GraphElement graphElement) {
    FragmentGenerator.GraphFragment fragment = myFragmentGenerator.getPartLongFragment(graphElement);
    if (fragment == null)
      return Collections.emptySet();

    final Set<Integer> selectedNodes = new HashSet<Integer>();
    selectedNodes.add(fragment.upNodeIndex);
    selectedNodes.add(fragment.downNodeIndex);

    myDfsUtil.nodeDfsIterator(fragment.upNodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int downNode : myPrintedLinearGraph.getDownNodes(currentNode)) {
          if (selectedNodes.add(downNode))
            return downNode;
        }
        return DfsUtil.NextNode.NODE_NOT_FOUND;
      }
    });

    return selectedNodes;
  }
}
