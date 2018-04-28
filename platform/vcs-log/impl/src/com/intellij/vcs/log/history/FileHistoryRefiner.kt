// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.graph.utils.BfsUtil;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.ObjectUtils.notNull;

class FileHistoryRefiner implements DfsUtil.NodeVisitor {
  @NotNull private final VisibleGraphImpl<Integer> myVisibleGraph;
  @NotNull private final PermanentCommitsInfo<Integer> myPermanentCommitsInfo;
  @NotNull private final LiteLinearGraph myPermanentLinearGraph;
  @NotNull private final IndexDataGetter.FileNamesData myNamesData;

  @NotNull private final Stack<FilePath> myPaths = new Stack<>();
  @NotNull private final BitSetFlags myVisibilityBuffer; // a reusable buffer for bfs
  @NotNull private final Map<Integer, FilePath> myPathsForCommits = ContainerUtil.newHashMap();
  @NotNull private final Set<Integer> myExcluded = ContainerUtil.newHashSet();

  public FileHistoryRefiner(@NotNull VisibleGraphImpl<Integer> visibleGraph,
                            @NotNull IndexDataGetter.FileNamesData namesData) {
    myVisibleGraph = visibleGraph;
    myPermanentCommitsInfo = myVisibleGraph.getPermanentGraph().getPermanentCommitsInfo();
    myPermanentLinearGraph = LinearGraphUtils.asLiteLinearGraph(myVisibleGraph.getPermanentGraph().getLinearGraph());
    myNamesData = namesData;

    myVisibilityBuffer = new BitSetFlags(myPermanentLinearGraph.nodesCount());
  }

  public boolean refine(int row, @NotNull FilePath startPath) {
    if (myNamesData.hasRenames()) {
      myPaths.push(startPath);
      DfsUtil.walk(LinearGraphUtils.asLiteLinearGraph(myVisibleGraph.getLinearGraph()), row, this);
    }
    else {
      myPathsForCommits.putAll(myNamesData.buildPathsMap());
    }

    for (int commit : myPathsForCommits.keySet()) {
      FilePath path = myPathsForCommits.get(commit);
      if (path != null) {
        if (!myNamesData.affects(commit, path)) myExcluded.add(commit);
        if (myNamesData.isTrivialMerge(commit, path)) myExcluded.add(commit);
      }
    }

    myExcluded.forEach(myPathsForCommits::remove);
    return !myExcluded.isEmpty();
  }

  @NotNull
  public Map<Integer, FilePath> getPathsForCommits() {
    return myPathsForCommits;
  }

  @Override
  public void enterNode(int currentNode, int previousNode, boolean down) {
    int currentNodeId = myVisibleGraph.getNodeId(currentNode);
    int currentCommit = myPermanentCommitsInfo.getCommitId(currentNodeId);

    FilePath previousPath = notNull(ContainerUtil.findLast(myPaths, path -> path != null));
    FilePath currentPath = previousPath;

    if (previousNode != DfsUtil.NextNode.NODE_NOT_FOUND) {
      int previousNodeId = myVisibleGraph.getNodeId(previousNode);
      int previousCommit = myPermanentCommitsInfo.getCommitId(previousNodeId);

      if (down) {
        Function<Integer, FilePath> pathGetter = parentIndex -> myNamesData
          .getPathInParentRevision(previousCommit, myPermanentCommitsInfo.getCommitId(parentIndex), previousPath);
        currentPath = findPathWithoutConflict(previousNodeId, pathGetter);
        if (currentPath == null) {
          int parentIndex = BfsUtil.getCorrespondingParent(myPermanentLinearGraph, previousNodeId, currentNodeId, myVisibilityBuffer);
          currentPath = pathGetter.fun(parentIndex);
        }
      }
      else {
        Function<Integer, FilePath> pathGetter =
          parentIndex -> myNamesData.getPathInChildRevision(currentCommit, myPermanentCommitsInfo.getCommitId(parentIndex), previousPath);
        currentPath = findPathWithoutConflict(currentNodeId, pathGetter);
        if (currentPath == null) {
          // since in reality there is no edge between the nodes, but the whole path, we need to know, which parent is affected by this path
          int parentIndex =
            BfsUtil.getCorrespondingParent(myPermanentLinearGraph, currentNodeId, previousNodeId, myVisibilityBuffer);
          currentPath = pathGetter.fun(parentIndex);
        }
      }
    }

    myPathsForCommits.put(currentCommit, currentPath);
    myPaths.push(currentPath);
  }

  @Nullable
  private FilePath findPathWithoutConflict(int nodeId, @NotNull Function<Integer, FilePath> pathGetter) {
    List<Integer> parents = myPermanentLinearGraph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN);
    FilePath path = pathGetter.fun(parents.get(0));
    if (parents.size() == 1) return path;

    for (Integer parent : ContainerUtil.subList(parents, 1)) {
      if (!Objects.equals(pathGetter.fun(parent), path)) {
        return null;
      }
    }
    return path;
  }

  @Override
  public void exitNode(int node) {
    myPaths.pop();
  }
}
