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
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfoImpl;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.visible.CommitCountStage;
import com.intellij.vcs.log.visible.VcsLogFilterer;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    IndexDataGetter.FileNamesData namesData = ((FilteredByFileResult)filterResult).fileNamesData;
    if (namesData.hasRenames() && visibleGraph.getVisibleCommitCount() > 0) {
      if (visibleGraph instanceof VisibleGraphImpl) {

        int row = getCurrentRow(dataPack, visibleGraph, namesData);
        if (row >= 0) {
          FileHistoryRefiner refiner = new FileHistoryRefiner(visibleGraph, namesData);
          if (refiner.refine(((VisibleGraphImpl)visibleGraph).getLinearGraph(), row, myFilePath)) {
            // creating a vg is the most expensive task, so trying to avoid that when unnecessary
            visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, refiner.getMatchingCommits());
          }
        }
      }
    }

    return new FileHistoryVisiblePack(dataPack, visibleGraph, filterResult.canRequestMore, filters, namesData);
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

      VcsLogDetailsFilter filter = ObjectUtils.notNull(ContainerUtil.getFirstItem(detailsFilters));
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
    @NotNull private final VisibleGraph<Integer> myVisibleGraph;
    @NotNull private final IndexDataGetter.FileNamesData myNamesData;
    @NotNull private final Stack<FilePath> myPaths;
    @NotNull private final Set<Integer> myMatchingCommits;

    private boolean myWasChanged;

    public FileHistoryRefiner(@NotNull VisibleGraph<Integer> visibleGraph,
                              @NotNull IndexDataGetter.FileNamesData namesData) {
      myVisibleGraph = visibleGraph;
      myNamesData = namesData;

      myPaths = new Stack<>();
      myMatchingCommits = ContainerUtil.newHashSet();
      myWasChanged = false;
    }

    public boolean refine(@NotNull LinearGraph graph, int row, @NotNull FilePath startPath) {
      myPaths.push(startPath);
      DfsUtil.walk(LinearGraphUtils.asLiteLinearGraph(graph), row, this);
      return myWasChanged;
    }

    @NotNull
    public Set<Integer> getMatchingCommits() {
      return myMatchingCommits;
    }

    @Override
    public void enterNode(int node) {
      FilePath currentPath = myPaths.peek();
      Integer commit = myVisibleGraph.getRowInfo(node).getCommit();

      FilePath previousPath = myNamesData.getPreviousPath(commit, currentPath);
      if (previousPath != null) {
        myMatchingCommits.add(commit);
        myPaths.push(previousPath);
        myNamesData.retain(commit, currentPath, previousPath);
      }
      else {
        myNamesData.remove(commit);
        myWasChanged = true;
      }
    }

    @Override
    public void exitNode(int node) {
      Integer commit = myVisibleGraph.getRowInfo(node).getCommit();
      if (myMatchingCommits.contains(commit)) {
        myPaths.pop();
      }
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
