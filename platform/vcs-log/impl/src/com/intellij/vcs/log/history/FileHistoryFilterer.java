/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CompressedRefs;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfoImpl;
import com.intellij.vcs.log.graph.utils.BfsUtil;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import com.intellij.vcs.log.visible.CommitCountStage;
import com.intellij.vcs.log.visible.VcsLogFilterer;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;

class FileHistoryFilterer extends VcsLogFilterer {
  private static final Logger LOG = Logger.getInstance(FileHistoryFilterer.class);

  @NotNull private final FilePath myFilePath;
  @NotNull private final IndexDataGetter myIndexDataGetter;
  @NotNull private final VirtualFile myRoot;

  public FileHistoryFilterer(@NotNull VcsLogData logData, @NotNull FilePath filePath) {
    super(logData.getLogProviders(), logData.getStorage(), logData.getTopCommitsCache(), logData.getCommitDetailsGetter(),
          logData.getIndex());
    myFilePath = filePath;
    myIndexDataGetter = ObjectUtils.assertNotNull(myIndex.getDataGetter());
    myRoot = ObjectUtils.assertNotNull(VcsUtil.getVcsRootFor(logData.getProject(), myFilePath));
  }

  @NotNull
  protected VisiblePack createVisiblePack(@NotNull DataPack dataPack,
                                          @NotNull PermanentGraph.SortType sortType,
                                          @NotNull VcsLogFilterCollection filters,
                                          @Nullable Set<Integer> matchingHeads,
                                          @NotNull FilterByDetailsResult filterResult) {
    if (!(filterResult instanceof FilteredByFileResult) || filterResult.matchingCommits == null) {
      return super.createVisiblePack(dataPack, sortType, filters, matchingHeads, filterResult);
    }

    VisibleGraph<Integer> visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, filterResult.matchingCommits);
    checkNotEmpty(dataPack, visibleGraph, false);

    IndexDataGetter.FileNamesData namesData = ((FilteredByFileResult)filterResult).fileNamesData;
    Map<Integer, FilePath> pathsMap = null;
    if (visibleGraph.getVisibleCommitCount() > 0) {
      if (visibleGraph instanceof VisibleGraphImpl) {
        int row = getCurrentRow(dataPack, visibleGraph, namesData);
        if (row >= 0) {
          FileHistoryRefiner refiner = new FileHistoryRefiner((VisibleGraphImpl)visibleGraph, namesData);
          if (refiner.refine(row, myFilePath)) {
            // creating a vg is the most expensive task, so trying to avoid that when unnecessary
            visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, refiner.getPathsForCommits().keySet());
            pathsMap = refiner.getPathsForCommits();
            checkNotEmpty(dataPack, visibleGraph, true);
          }
        }
      }
    }

    if (pathsMap == null) {
      pathsMap = namesData.buildPathsMap();
    }

    return new FileHistoryVisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters, pathsMap);
  }

  private void checkNotEmpty(@NotNull DataPack dataPack, @NotNull VisibleGraph visibleGraph, boolean refined) {
    if (!dataPack.isFull()) {
      if (!refined) {
        LOG.debug("Data pack is not full while computing file history for " + myFilePath + "\n" +
                  "Found " + visibleGraph.getVisibleCommitCount() + " commits");
      }
    }
    else if (visibleGraph.getVisibleCommitCount() == 0) {
      LOG.warn("Empty" + (refined ? " refined " : " ") + "file history for " + myFilePath);
    }
  }

  @NotNull
  @Override
  protected FilterByDetailsResult filterByDetails(@NotNull DataPack dataPack,
                                                  @NotNull VcsLogFilterCollection filters,
                                                  @NotNull CommitCountStage commitCount,
                                                  @NotNull Collection<VirtualFile> visibleRoots,
                                                  @Nullable Set<Integer> matchingHeads) {
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();

    if (myIndex.isIndexed(myRoot)) {
      // checking our assumptions:
      // we have one file filter here

      LOG.assertTrue(detailsFilters.size() == 1);

      VcsLogDetailsFilter filter = notNull(ContainerUtil.getFirstItem(detailsFilters));
      LOG.assertTrue(filter instanceof VcsLogStructureFilter);
      LOG.assertTrue(((VcsLogStructureFilter)filter).getFiles().equals(Collections.singleton(myFilePath)));

      IndexDataGetter.FileNamesData data = myIndexDataGetter.buildFileNamesData(myFilePath);
      return new FilteredByFileResult(data, data.getCommits(), false, commitCount);
    }

    return super.filterByDetails(dataPack, filters, commitCount, visibleRoots, matchingHeads);
  }

  private int getCurrentRow(@NotNull DataPack pack,
                            @NotNull VisibleGraph<Integer> visibleGraph,
                            @NotNull IndexDataGetter.FileNamesData fileIndexData) {
    PermanentGraph<Integer> permanentGraph = pack.getPermanentGraph();
    if (permanentGraph instanceof PermanentGraphImpl) {
      CompressedRefs refs = pack.getRefsModel().getAllRefsByRoot().get(myRoot);
      Optional<VcsRef> headOptional = refs.streamBranches().filter(br -> br.getName().equals("HEAD")).findFirst();
      if (headOptional.isPresent()) {
        VcsRef head = headOptional.get();
        assert head.getRoot().equals(myRoot);
        return findAncestorRowAffectingFile((PermanentGraphImpl<Integer>)permanentGraph, head.getCommitHash(), visibleGraph, fileIndexData);
      }
    }
    return -1;
  }

  private int findAncestorRowAffectingFile(@NotNull PermanentGraphImpl<Integer> permanentGraph,
                                           @NotNull Hash hash,
                                           @NotNull VisibleGraph<Integer> visibleGraph,
                                           @NotNull IndexDataGetter.FileNamesData fileNamesData) {
    Ref<Integer> result = new Ref<>();

    PermanentCommitsInfoImpl<Integer> commitsInfo = permanentGraph.getPermanentCommitsInfo();
    ReachableNodes reachableNodes = new ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentGraph.getLinearGraph()));
    reachableNodes.walk(Collections.singleton(commitsInfo.getNodeId(myStorage.getCommitIndex(hash, myRoot))), true,
                        currentNode -> {
                          int id = commitsInfo.getCommitId(currentNode);
                          if (fileNamesData.affects(id, myFilePath)) {
                            result.set(currentNode);
                            return false; // stop walk, we have found it
                          }
                          return true; // continue walk
                        });

    if (!result.isNull()) {
      Integer rowIndex = visibleGraph.getVisibleRowIndex(commitsInfo.getCommitId(result.get()));
      return ObjectUtils.assertNotNull(rowIndex);
    }

    return -1;
  }

  private static class FileHistoryRefiner implements DfsUtil.NodeVisitor {
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

  private static class FilteredByFileResult extends FilterByDetailsResult {
    @NotNull public final IndexDataGetter.FileNamesData fileNamesData;

    public FilteredByFileResult(@NotNull IndexDataGetter.FileNamesData data,
                                @Nullable Set<Integer> commits,
                                boolean more,
                                @NotNull CommitCountStage count) {
      super(commits, more, count);
      fileNamesData = data;
    }
  }
}
