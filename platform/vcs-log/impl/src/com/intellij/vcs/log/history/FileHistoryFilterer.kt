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
import com.intellij.openapi.vcs.history.VcsCachingHistory;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsFileRevisionEx;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphCommitImpl;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfoImpl;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.impl.VcsLogRevisionFilterImpl;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.CommitCountStage;
import com.intellij.vcs.log.visible.VcsLogFilterer;
import com.intellij.vcs.log.visible.VcsLogFiltererImpl;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

class FileHistoryFilterer implements VcsLogFilterer {
  private static final Logger LOG = Logger.getInstance(FileHistoryFilterer.class);

  @NotNull private final Project myProject;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final VcsLogStorage myStorage;
  @NotNull private final VcsLogIndex myIndex;
  @NotNull private final IndexDataGetter myIndexDataGetter;
  @NotNull private final VcsLogFiltererImpl myVcsLogFilterer;

  public FileHistoryFilterer(@NotNull VcsLogData logData) {
    myProject = logData.getProject();
    myLogProviders = logData.getLogProviders();
    myStorage = logData.getStorage();
    myIndex = logData.getIndex();
    myIndexDataGetter = assertNotNull(myIndex.getDataGetter());

    myVcsLogFilterer = new VcsLogFiltererImpl(myLogProviders, myStorage, logData.getTopCommitsCache(),
                                              logData.getCommitDetailsGetter(), myIndex);
  }

  @NotNull
  @Override
  public Pair<VisiblePack, CommitCountStage> filter(@NotNull DataPack dataPack,
                                                    @NotNull PermanentGraph.SortType sortType,
                                                    @NotNull VcsLogFilterCollection filters,
                                                    @NotNull CommitCountStage commitCount) {
    FilePath filePath = getFilePath(filters);
    if (filePath == null) {
      return myVcsLogFilterer.filter(dataPack, sortType, filters, commitCount);
    }
    VirtualFile root = notNull(VcsUtil.getVcsRootFor(myProject, filePath));
    return new MyWorker(root, filePath, getHash(filters)).filter(dataPack, sortType, filters, commitCount);
  }

  @Override
  public boolean canFilterEmptyPack(@NotNull VcsLogFilterCollection filters) {
    FilePath filePath = getFilePath(filters);
    return filePath != null && !filePath.isDirectory();
  }

  @Nullable
  private static FilePath getFilePath(@NotNull VcsLogFilterCollection filters) {
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();
    if (detailsFilters.size() != 1) {
      return null;
    }

    VcsLogDetailsFilter filter = notNull(getFirstItem(detailsFilters));
    if (!(filter instanceof VcsLogStructureFilter)) {
      return null;
    }

    Collection<FilePath> files = ((VcsLogStructureFilter)filter).getFiles();
    if (files.size() != 1) {
      return null;
    }

    return notNull(getFirstItem(files));
  }

  @Nullable
  private static Hash getHash(@NotNull VcsLogFilterCollection filters) {
    VcsLogRevisionFilter revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER);
    if (revisionFilter == null) {
      return null;
    }

    Collection<CommitId> heads = revisionFilter.getHeads();
    if (heads.size() != 1) {
      return null;
    }

    return notNull(getFirstItem(heads)).getHash();
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

  private class MyWorker {
    @NotNull private final VirtualFile myRoot;
    @NotNull private final FilePath myFilePath;
    @Nullable private final Hash myHash;

    private MyWorker(@NotNull VirtualFile root, @NotNull FilePath path, @Nullable Hash hash) {
      myRoot = root;
      myFilePath = path;
      myHash = hash;
    }

    @NotNull
    public Pair<VisiblePack, CommitCountStage> filter(@NotNull DataPack dataPack,
                                                      @NotNull PermanentGraph.SortType sortType,
                                                      @NotNull VcsLogFilterCollection filters,
                                                      @NotNull CommitCountStage commitCount) {
      long start = System.currentTimeMillis();

      if (myIndex.isIndexed(myRoot) && (dataPack.isFull() || myFilePath.isDirectory())) {
        VisiblePack visiblePack = filterWithIndex(dataPack, sortType, filters);
        LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for " + myFilePath + " with index");
        checkNotEmpty(dataPack, visiblePack, true);
        return Pair.create(visiblePack, commitCount);
      }

      if (myFilePath.isDirectory()) {
        return myVcsLogFilterer.filter(dataPack, sortType, filters, commitCount);
      }

      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(myRoot);
      if (vcs != null && vcs.getVcsHistoryProvider() != null) {
        try {
          VisiblePack visiblePack = filterWithProvider(vcs, dataPack, sortType, filters);
          LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                    " for computing history for " +
                    myFilePath +
                    " with history provider");
          checkNotEmpty(dataPack, visiblePack, false);
          return Pair.create(visiblePack, commitCount);
        }
        catch (VcsException e) {
          LOG.error(e);
          return myVcsLogFilterer.filter(dataPack, sortType, filters, commitCount);
        }
      }

      LOG.warn("Could not find vcs or history provider for file " + myFilePath);
      return myVcsLogFilterer.filter(dataPack, sortType, filters, commitCount);
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

    @NotNull
    private VisiblePack filterWithProvider(@NotNull AbstractVcs vcs,
                                           @NotNull DataPack dataPack,
                                           @NotNull PermanentGraph.SortType sortType,
                                           @NotNull VcsLogFilterCollection filters) throws VcsException {
      VcsRevisionNumber revisionNumber = myHash != null ? VcsLogUtil.convertToRevisionNumber(myHash) : null;
      List<VcsFileRevision> revisions = VcsCachingHistory.collect(vcs, myFilePath, revisionNumber);

      if (revisions.isEmpty()) return VisiblePack.EMPTY;

      Map<Integer, FilePath> pathsMap = ContainerUtil.newHashMap();
      VisibleGraph<Integer> visibleGraph;

      if (dataPack.isFull()) {
        for (VcsFileRevision revision : revisions) {
          pathsMap.put(getIndex(revision), ((VcsFileRevisionEx)revision).getPath());
        }
        visibleGraph = myVcsLogFilterer.createVisibleGraph(dataPack, sortType, null, pathsMap.keySet());
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
        visibleGraph = myVcsLogFilterer.createVisibleGraph(dataPack, sortType, null,
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
      Set<Integer> matchingHeads = myVcsLogFilterer.getMatchingHeads(dataPack.getRefsModel(), Collections.singleton(myRoot), filters);
      IndexDataGetter.FileNamesData data = myIndexDataGetter.buildFileNamesData(myFilePath);
      VisibleGraph<Integer> visibleGraph = myVcsLogFilterer.createVisibleGraph(dataPack, sortType, matchingHeads, data.getCommits());

      Map<Integer, FilePath> pathsMap = null;
      if (visibleGraph.getVisibleCommitCount() > 0) {
        if (visibleGraph instanceof VisibleGraphImpl) {
          int row = getCurrentRow(dataPack, visibleGraph, data);
          if (row >= 0) {
            FileHistoryRefiner refiner = new FileHistoryRefiner((VisibleGraphImpl<Integer>)visibleGraph, data);
            if (refiner.refine(row, myFilePath)) {
              // creating a vg is the most expensive task, so trying to avoid that when unnecessary
              visibleGraph = myVcsLogFilterer.createVisibleGraph(dataPack, sortType, matchingHeads, refiner.getPathsForCommits().keySet());
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
        return assertNotNull(rowIndex);
      }

      return -1;
    }
  }
}
