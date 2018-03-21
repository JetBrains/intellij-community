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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphCommitImpl;
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
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.impl.VcsLogRevisionFilterImpl;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.CommitCountStage;
import com.intellij.vcs.log.visible.VcsLogFilterer;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;

class FileHistoryFilterer extends VcsLogFilterer {
  private static final Logger LOG = Logger.getInstance(FileHistoryFilterer.class);

  @NotNull private final Project myProject;
  @NotNull private final FilePath myFilePath;
  @Nullable private final Hash myHash;
  @NotNull private final IndexDataGetter myIndexDataGetter;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final VcsHistoryCache myVcsHistoryCache;

  public FileHistoryFilterer(@NotNull VcsLogData logData, @NotNull FilePath filePath, @Nullable Hash hash, @NotNull VirtualFile root) {
    super(logData.getLogProviders(), logData.getStorage(), logData.getTopCommitsCache(), logData.getCommitDetailsGetter(),
          logData.getIndex());
    myProject = logData.getProject();
    myFilePath = filePath;
    myHash = hash;
    myRoot = root;
    myIndexDataGetter = ObjectUtils.assertNotNull(myIndex.getDataGetter());
    myVcsHistoryCache = ProjectLevelVcsManager.getInstance(myProject).getVcsHistoryCache();
  }

  @NotNull
  @Override
  protected Pair<VisiblePack, CommitCountStage> filter(@NotNull DataPack dataPack,
                                                       @NotNull PermanentGraph.SortType sortType,
                                                       @NotNull VcsLogFilterCollection filters,
                                                       @NotNull CommitCountStage commitCount) {
    long start = System.currentTimeMillis();

    checkDetailsFilter(filters);

    if (myIndex.isIndexed(myRoot) && (dataPack.isFull() || myFilePath.isDirectory())) {
      VisiblePack visiblePack = filterWithIndex(dataPack, sortType, filters);
      LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for " + myFilePath + " with index");
      checkNotEmpty(dataPack, visiblePack, true);
      return Pair.create(visiblePack, commitCount);
    }

    if (myFilePath.isDirectory()) {
      return super.filter(dataPack, sortType, filters, commitCount);
    }

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(myRoot);
    if (vcs != null) {
      VcsHistoryProvider provider = vcs.getVcsHistoryProvider();
      if (provider != null) {
        try {
          VisiblePack visiblePack = filterWithProvider(vcs, provider, dataPack, sortType, filters);
          LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                    " for computing history for " +
                    myFilePath +
                    " with history provider");
          checkNotEmpty(dataPack, visiblePack, false);
          return Pair.create(visiblePack, commitCount);
        }
        catch (VcsException e) {
          LOG.error(e);
          return super.filter(dataPack, sortType, filters, commitCount);
        }
      }
    }

    LOG.warn("Could not find vcs or history provider for file " + myFilePath);
    return super.filter(dataPack, sortType, filters, commitCount);
  }

  private void checkNotEmpty(@NotNull DataPack dataPack, @NotNull VisiblePack visiblePack, boolean withIndex) {
    if (!dataPack.isFull()) {
      LOG.debug("Data pack is not full while computing file history for " + myFilePath + "\n" +
                "Found " + visiblePack.getVisibleGraph().getVisibleCommitCount() + " commits");
    }
    else if (visiblePack.getVisibleGraph().getVisibleCommitCount() == 0) {
      LOG.warn("Empty file history from " + (withIndex ? "index" : "provider") + " for " + myFilePath);
    }
  }

  @Override
  public boolean canBuildFromEmpty() {
    return !myFilePath.isDirectory();
  }

  @NotNull
  private VisiblePack filterWithProvider(@NotNull AbstractVcs vcs, @NotNull VcsHistoryProvider provider,
                                         @NotNull DataPack dataPack,
                                         @NotNull PermanentGraph.SortType sortType,
                                         @NotNull VcsLogFilterCollection filters) throws VcsException {
    VcsAbstractHistorySession session = null;
    if (provider instanceof VcsCacheableHistorySessionFactory && myHash == null) {
      session = myVcsHistoryCache.getFull(myFilePath, vcs.getKeyInstanceMethod(), (VcsCacheableHistorySessionFactory)provider);
    }

    if (session == null || session.getRevisionList().isEmpty() || session.shouldBeRefreshed()) {
      VcsAppendableHistoryPartnerAdapter partner = new VcsAppendableHistoryPartnerAdapter();
      if (provider instanceof VcsHistoryProviderEx && myHash != null) {
        ((VcsHistoryProviderEx)provider).reportAppendableHistory(myFilePath, VcsLogUtil.convertToRevisionNumber(myHash), partner);
      }
      else {
        provider.reportAppendableHistory(myFilePath, partner);
      }
      session = partner.getSession();

      if (provider instanceof VcsCacheableHistorySessionFactory && myHash == null) {
        myVcsHistoryCache.put(myFilePath, null, vcs.getKeyInstanceMethod(), session, (VcsCacheableHistorySessionFactory)provider, true);
      }
    }

    List<VcsFileRevision> revisions = session.getRevisionList();
    if (revisions.isEmpty()) return VisiblePack.EMPTY;

    Map<Integer, FilePath> pathsMap = ContainerUtil.newHashMap();
    VisibleGraph<Integer> visibleGraph;

    if (dataPack.isFull()) {
      for (VcsFileRevision revision : revisions) {
        pathsMap.put(getIndex(revision), ((VcsFileRevisionEx)revision).getPath());
      }
      visibleGraph = createVisibleGraph(dataPack, sortType, null, pathsMap.keySet());
    }
    else {
      List<GraphCommit<Integer>> commits = ContainerUtil.newArrayListWithCapacity(revisions.size());

      for (VcsFileRevision revision : revisions) {
        int index = getIndex(revision);
        pathsMap.put(index, ((VcsFileRevisionEx)revision).getPath());
        commits.add(GraphCommitImpl.createCommit(index, Collections.emptyList(), revision.getRevisionDate().getTime()));
      }

      Map<VirtualFile, CompressedRefs> refs = getFilteredRefs(dataPack);
      Map<VirtualFile, VcsLogProvider> providers = ContainerUtil.newHashMap(Pair.create(myRoot, myLogProviders.get(myRoot)));

      dataPack = DataPack.build(commits, refs, providers, myStorage, false);
      visibleGraph = createVisibleGraph(dataPack, sortType, null,
                                        null/*no need to filter here, since we do not have any extra commits in this pack*/);
    }

    return new FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, pathsMap);
  }

  @NotNull
  private Map<VirtualFile, CompressedRefs> getFilteredRefs(@NotNull DataPack dataPack) {
    Map<VirtualFile, CompressedRefs> refs = ContainerUtil.newHashMap();
    CompressedRefs compressedRefs = dataPack.getRefsModel().getAllRefsByRoot().get(myRoot);
    if (compressedRefs == null) {
      compressedRefs = new CompressedRefs(ContainerUtil.newHashSet(), myStorage);
    }
    refs.put(myRoot, compressedRefs);
    return refs;
  }

  private int getIndex(@NotNull VcsFileRevision revision) {
    return myStorage.getCommitIndex(HashImpl.build(revision.getRevisionNumber().asString()), myRoot);
  }

  @NotNull
  private VisiblePack filterWithIndex(@NotNull DataPack dataPack,
                                      @NotNull PermanentGraph.SortType sortType,
                                      @NotNull VcsLogFilterCollection filters) {
    Set<Integer> matchingHeads = getMatchingHeads(dataPack.getRefsModel(), Collections.singleton(myRoot), filters);
    IndexDataGetter.FileNamesData data = myIndexDataGetter.buildFileNamesData(myFilePath);
    VisibleGraph<Integer> visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, data.getCommits());

    Map<Integer, FilePath> pathsMap = null;
    if (visibleGraph.getVisibleCommitCount() > 0) {
      if (visibleGraph instanceof VisibleGraphImpl) {
        int row = getCurrentRow(dataPack, visibleGraph, data);
        if (row >= 0) {
          FileHistoryRefiner refiner = new FileHistoryRefiner((VisibleGraphImpl<Integer>)visibleGraph, data);
          if (refiner.refine(row, myFilePath)) {
            // creating a vg is the most expensive task, so trying to avoid that when unnecessary
            visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, refiner.getPathsForCommits().keySet());
            pathsMap = refiner.getPathsForCommits();
          }
        }
      }
    }

    if (pathsMap == null) {
      pathsMap = data.buildPathsMap();
    }

    if (!myFilePath.isDirectory()) reindexFirstCommitsIfNeeded(visibleGraph);
    return new FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, pathsMap);
  }

  private void reindexFirstCommitsIfNeeded(@NotNull VisibleGraph<Integer> graph) {
    // we may not have renames big commits, may need to reindex them
    if (graph instanceof VisibleGraphImpl) {
      LiteLinearGraph liteLinearGraph = LinearGraphUtils.asLiteLinearGraph(((VisibleGraphImpl)graph).getLinearGraph());
      for (int row = 0; row < liteLinearGraph.nodesCount(); row++) {
        // checking if commit is a root commit (which means file was added or renamed there)
        if (liteLinearGraph.getNodes(row, LiteLinearGraph.NodeFilter.DOWN).isEmpty()) {
          myIndex.reindexWithRenames(graph.getRowInfo(row).getCommit(), myRoot);
        }
      }
    }
  }

  private void checkDetailsFilter(@NotNull VcsLogFilterCollection filters) {
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();
    // checking our assumptions:
    // we have one file filter here
    LOG.assertTrue(detailsFilters.size() == 1);

    VcsLogDetailsFilter filter = notNull(ContainerUtil.getFirstItem(detailsFilters));
    LOG.assertTrue(filter instanceof VcsLogStructureFilter);
    LOG.assertTrue(((VcsLogStructureFilter)filter).getFiles().equals(Collections.singleton(myFilePath)));
  }

  @NotNull
  public static VcsLogFilterCollection createFilters(@NotNull FilePath path,
                                                     @Nullable Hash revision,
                                                     @NotNull VirtualFile root,
                                                     boolean showAllBranches) {
    VcsLogStructureFilterImpl fileFilter = new VcsLogStructureFilterImpl(Collections.singleton(path));

    if (revision != null) {
      VcsLogRevisionFilterImpl revisionFilter = VcsLogRevisionFilterImpl.fromCommit(new CommitId(revision, root));
      return new VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder(fileFilter, revisionFilter).build();
    }

    VcsLogBranchFilterImpl branchFilter = showAllBranches ? null : VcsLogBranchFilterImpl.fromBranch("HEAD");
    return new VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder(fileFilter, branchFilter).build();
  }

  @NotNull
  public VcsLogFilterCollection createFilters(boolean showAllBranches) {
    return createFilters(myFilePath, myHash, myRoot, showAllBranches);
  }

  private int getCurrentRow(@NotNull DataPack pack,
                            @NotNull VisibleGraph<Integer> visibleGraph,
                            @NotNull IndexDataGetter.FileNamesData fileIndexData) {
    PermanentGraph<Integer> permanentGraph = pack.getPermanentGraph();
    if (permanentGraph instanceof PermanentGraphImpl) {
      Hash hash = myHash != null ? myHash : getHead(pack);
      if (hash != null) {
        return findAncestorRowAffectingFile((PermanentGraphImpl<Integer>)permanentGraph, hash, visibleGraph, fileIndexData);
      }
    }
    return 0;
  }

  @Nullable
  private Hash getHead(@NotNull DataPack pack) {
    CompressedRefs refs = pack.getRefsModel().getAllRefsByRoot().get(myRoot);
    Optional<VcsRef> headOptional = refs.streamBranches().filter(br -> br.getName().equals("HEAD")).findFirst();
    if (headOptional.isPresent()) {
      VcsRef head = headOptional.get();
      assert head.getRoot().equals(myRoot);
      return head.getCommitHash();
    }
    return null;
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
}
