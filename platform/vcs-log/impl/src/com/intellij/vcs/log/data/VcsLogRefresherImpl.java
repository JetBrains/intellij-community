/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphCommitImpl;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.RequirementsImpl;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsLogRefresherImpl implements VcsLogRefresher {

  private static final Logger LOG = Logger.getInstance(VcsLogRefresherImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogHashMap myHashMap;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myProviders;
  @NotNull private final VcsUserRegistryImpl myUserRegistry;
  @NotNull private final Map<Integer, VcsCommitMetadata> myTopCommitsDetailsCache;
  @NotNull private final Consumer<Exception> myExceptionHandler;
  private final int myRecentCommitCount;

  @NotNull private final SingleTaskController<RefreshRequest, DataPack> mySingleTaskController;

  @NotNull private volatile DataPack myDataPack = DataPack.EMPTY;

  public VcsLogRefresherImpl(@NotNull final Project project,
                             @NotNull VcsLogHashMap hashMap,
                             @NotNull Map<VirtualFile, VcsLogProvider> providers,
                             @NotNull final VcsUserRegistryImpl userRegistry,
                             @NotNull Map<Integer, VcsCommitMetadata> topCommitsDetailsCache,
                             @NotNull final Consumer<DataPack> dataPackUpdateHandler,
                             @NotNull Consumer<Exception> exceptionHandler,
                             int recentCommitsCount) {
    myProject = project;
    myHashMap = hashMap;
    myProviders = providers;
    myUserRegistry = userRegistry;
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myExceptionHandler = exceptionHandler;
    myRecentCommitCount = recentCommitsCount;

    mySingleTaskController = new SingleTaskController<RefreshRequest, DataPack>(new Consumer<DataPack>() {
      @Override
      public void consume(@NotNull DataPack dataPack) {
        myDataPack = dataPack;
        dataPackUpdateHandler.consume(dataPack);
      }
    }) {
      @Override
      protected void startNewBackgroundTask() {
        VcsLogRefresherImpl.this.startNewBackgroundTask(new MyRefreshTask(myDataPack));
      }
    };
  }

  protected void startNewBackgroundTask(@NotNull final Task.Backgroundable refreshTask) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        LOG.debug("Starting a background task...");
        ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(refreshTask);
      }
    });
  }

  @NotNull
  public DataPack getCurrentDataPack() {
    return myDataPack;
  }

  @NotNull
  @Override
  public DataPack readFirstBlock() {
    try {
      LogInfo data = loadRecentData(new CommitCountRequirements(myRecentCommitCount).asMap(myProviders.keySet()));
      Collection<List<GraphCommit<Integer>>> commits = data.getCommits();
      Map<VirtualFile, Set<VcsRef>> refs = data.getRefs();
      List<GraphCommit<Integer>> compoundList = multiRepoJoin(commits);
      compoundList = compoundList.subList(0, Math.min(myRecentCommitCount, compoundList.size()));
      myDataPack = DataPack.build(compoundList, refs, myProviders, myHashMap, false);
      mySingleTaskController.request(RefreshRequest.RELOAD_ALL); // build/rebuild the full log in background
      return myDataPack;
    }
    catch (VcsException e) {
      myExceptionHandler.consume(e);
      return DataPack.EMPTY;
    }
  }

  @NotNull
  private LogInfo loadRecentData(@NotNull final Map<VirtualFile, VcsLogProvider.Requirements> requirements) throws VcsException {
    final StopWatch sw = StopWatch.start("loading commits");
    final LogInfo logInfo = new LogInfo();
    new ProviderIterator() {
      @Override
      public void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
        VcsLogProvider.DetailedLogData data = provider.readFirstBlock(root, requirements.get(root));
        storeUsersAndDetails(data.getCommits());
        logInfo.put(root, compactCommits(data.getCommits(), root));
        logInfo.put(root, data.getRefs());
        sw.rootCompleted(root);
      }
    }.iterate(getProvidersForRoots(requirements.keySet()));
    myUserRegistry.flush();
    sw.report();
    return logInfo;
  }

  @NotNull
  private Map<VirtualFile, VcsLogProvider> getProvidersForRoots(@NotNull Set<VirtualFile> roots) {
    return ContainerUtil.map2Map(roots,
                                 new Function<VirtualFile, Pair<VirtualFile, VcsLogProvider>>() {
                                   @Override
                                   public Pair<VirtualFile, VcsLogProvider> fun(VirtualFile root) {
                                     return Pair.create(root, myProviders.get(root));
                                   }
                                 });
  }

  @Override
  public void refresh(@NotNull Collection<VirtualFile> rootsToRefresh) {
    if (!rootsToRefresh.isEmpty()) {
      mySingleTaskController.request(new RefreshRequest(rootsToRefresh));
    }
  }

  @NotNull
  private static <T extends GraphCommit<Integer>> List<T> multiRepoJoin(@NotNull Collection<List<T>> commits) {
    StopWatch sw = StopWatch.start("multi-repo join");
    List<T> joined = new VcsLogMultiRepoJoiner<Integer, T>().join(commits);
    sw.report();
    return joined;
  }

  @NotNull
  private List<GraphCommit<Integer>> compactCommits(@NotNull List<? extends TimedVcsCommit> commits, @NotNull final VirtualFile root) {
    StopWatch sw = StopWatch.start("compacting commits");
    List<GraphCommit<Integer>> map = ContainerUtil.map(commits, new Function<TimedVcsCommit, GraphCommit<Integer>>() {
      @NotNull
      @Override
      public GraphCommit<Integer> fun(@NotNull TimedVcsCommit commit) {
        return compactCommit(commit, root);
      }
    });
    myHashMap.flush();
    sw.report();
    return map;
  }

  @NotNull
  private GraphCommitImpl<Integer> compactCommit(@NotNull TimedVcsCommit commit, @NotNull final VirtualFile root) {
    List<Integer> parents = ContainerUtil.map(commit.getParents(), new NotNullFunction<Hash, Integer>() {
      @NotNull
      @Override
      public Integer fun(Hash hash) {
        return myHashMap.getCommitIndex(hash, root);
      }
    });
    return new GraphCommitImpl<Integer>(myHashMap.getCommitIndex(commit.getId(), root), parents, commit.getTimestamp());
  }

  private void storeUsersAndDetails(@NotNull Collection<? extends VcsCommitMetadata> metadatas) {
    for (VcsCommitMetadata detail : metadatas) {
      myUserRegistry.addUser(detail.getAuthor());
      myUserRegistry.addUser(detail.getCommitter());
      myTopCommitsDetailsCache.put(myHashMap.getCommitIndex(detail.getId(), detail.getRoot()), detail);
    }
  }

  private class MyRefreshTask extends Task.Backgroundable {

    @NotNull private DataPack myCurrentDataPack;

    @NotNull private final LogInfo myLoadedInfo = new LogInfo();

    MyRefreshTask(@NotNull DataPack currentDataPack) {
      super(VcsLogRefresherImpl.this.myProject, "Refreshing History...", false);
      myCurrentDataPack = currentDataPack;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      LOG.debug("Refresh task started");
      indicator.setIndeterminate(true);
      DataPack dataPack = myCurrentDataPack;
      while (true) {
        List<RefreshRequest> requests = mySingleTaskController.popRequests();
        Collection<VirtualFile> rootsToRefresh = getRootsToRefresh(requests);
        LOG.debug("Requests: " + requests + ". roots to refresh: " + rootsToRefresh);
        if (rootsToRefresh.isEmpty()) {
          mySingleTaskController.taskCompleted(dataPack);
          break;
        }
        dataPack = doRefresh(rootsToRefresh);
      }
    }

    @NotNull
    private Collection<VirtualFile> getRootsToRefresh(@NotNull List<RefreshRequest> requests) {
      Collection<VirtualFile> rootsToRefresh = ContainerUtil.newArrayList();
      for (RefreshRequest request : requests) {
        if (request == RefreshRequest.RELOAD_ALL) {
          myCurrentDataPack = DataPack.EMPTY;
          return myProviders.keySet();
        }
        rootsToRefresh.addAll(request.rootsToRefresh);
      }
      return rootsToRefresh;
    }

    @NotNull
    private DataPack doRefresh(@NotNull Collection<VirtualFile> roots) {
      StopWatch sw = StopWatch.start("refresh");
      PermanentGraph<Integer> permanentGraph = myCurrentDataPack.isFull() ? myCurrentDataPack.getPermanentGraph() : null;
      Map<VirtualFile, Set<VcsRef>> currentRefs = myCurrentDataPack.getRefsModel().getAllRefsByRoot();
      try {
        if (permanentGraph != null) {
          int commitCount = myRecentCommitCount;
          for (int attempt = 0; attempt <= 1; attempt++) {
            loadLogAndRefs(roots, currentRefs, commitCount);
            List<? extends GraphCommit<Integer>> compoundLog = multiRepoJoin(myLoadedInfo.getCommits());
            Map<VirtualFile, Set<VcsRef>> allNewRefs = getAllNewRefs(myLoadedInfo, currentRefs);
            List<GraphCommit<Integer>> joinedFullLog = join(compoundLog, permanentGraph.getAllCommits(), currentRefs, allNewRefs);
            if (joinedFullLog == null) {
              commitCount *= 5;
            }
            else {
              return DataPack.build(joinedFullLog, allNewRefs, myProviders, myHashMap, true);
            }
          }
          // couldn't join => need to reload everything; if 5000 commits is still not enough, it's worth reporting:
          LOG.info("Couldn't join " + commitCount / 5 + " recent commits to the log (" +
                   permanentGraph.getAllCommits().size() + " commits)");
        }

        return loadFullLog();
      }
      catch (Exception e) {
        myExceptionHandler.consume(e);
        return DataPack.EMPTY;
      }
      finally {
        sw.report();
      }
    }

    @NotNull
    private Map<VirtualFile, Set<VcsRef>> getAllNewRefs(@NotNull LogInfo newInfo,
                                                        @NotNull Map<VirtualFile, Set<VcsRef>> previousRefs) {
      Map<VirtualFile, Set<VcsRef>> result = ContainerUtil.newHashMap();
      for (VirtualFile root : previousRefs.keySet()) {
        Set<VcsRef> newInfoRefs = newInfo.getRefs(root);
        result.put(root, newInfoRefs != null ? newInfoRefs : previousRefs.get(root));
      }
      return result;
    }

    private void loadLogAndRefs(@NotNull Collection<VirtualFile> roots,
                                @NotNull Map<VirtualFile, Set<VcsRef>> prevRefs,
                                int commitCount) throws VcsException {
      LogInfo logInfo = loadRecentData(prepareRequirements(roots, commitCount, prevRefs));
      for (VirtualFile root : roots) {
        myLoadedInfo.put(root, logInfo.getCommits(root));
        myLoadedInfo.put(root, logInfo.getRefs(root));
      }
    }

    @NotNull
    private Map<VirtualFile, VcsLogProvider.Requirements> prepareRequirements(@NotNull Collection<VirtualFile> roots,
                                                                              int commitCount,
                                                                              @NotNull Map<VirtualFile, Set<VcsRef>> prevRefs) {
      Map<VirtualFile, VcsLogProvider.Requirements> requirements = ContainerUtil.newHashMap();
      for (VirtualFile root : roots) {
        requirements.put(root, new RequirementsImpl(commitCount, true, ContainerUtil.notNullize(prevRefs.get(root))));
      }
      return requirements;
    }

    @Nullable
    private List<GraphCommit<Integer>> join(@NotNull List<? extends GraphCommit<Integer>> recentCommits,
                                            @NotNull List<GraphCommit<Integer>> fullLog,
                                            @NotNull Map<VirtualFile, Set<VcsRef>> previousRefs,
                                            @NotNull Map<VirtualFile, Set<VcsRef>> newRefs) {
      StopWatch sw = StopWatch.start("joining new commits");
      Function<VcsRef, Integer> ref2Int = new Function<VcsRef, Integer>() {
        @Override
        public Integer fun(@NotNull VcsRef ref) {
          return myHashMap.getCommitIndex(ref.getCommitHash(), ref.getRoot());
        }
      };
      Collection<Integer> prevRefIndices = ContainerUtil.map(ContainerUtil.concat(previousRefs.values()), ref2Int);
      Collection<Integer> newRefIndices = ContainerUtil.map(ContainerUtil.concat(newRefs.values()), ref2Int);
      try {
        List<GraphCommit<Integer>> commits = new VcsLogJoiner<Integer, GraphCommit<Integer>>().addCommits(fullLog, prevRefIndices,
                                                                                                          recentCommits,
                                                                                                          newRefIndices).first;
        sw.report();
        return commits;
      }
      catch (VcsLogRefreshNotEnoughDataException e) {
        // valid case: e.g. another developer merged a long-developed branch, or we just didn't pull for a long time
        LOG.info(e);
      }
      catch (IllegalStateException e) {
        // it happens from time to time, but we don't know why, and can hardly debug it.
        LOG.info(e);
      }
      return null;
    }

    @NotNull
    private DataPack loadFullLog() throws VcsException {
      StopWatch sw = StopWatch.start("full log reload");
      LogInfo logInfo = readFullLogFromVcs();
      List<? extends GraphCommit<Integer>> graphCommits = multiRepoJoin(logInfo.getCommits());
      DataPack dataPack = DataPack.build(graphCommits, logInfo.getRefs(), myProviders, myHashMap, true);
      sw.report();
      return dataPack;
    }

    @NotNull
    private LogInfo readFullLogFromVcs() throws VcsException {
      final StopWatch sw = StopWatch.start("read full log from VCS");
      final LogInfo logInfo = new LogInfo();
      new ProviderIterator() {
        @Override
        void each(@NotNull final VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
          final List<GraphCommit<Integer>> graphCommits = ContainerUtil.newArrayList();
          VcsLogProvider.LogData data = provider.readAllHashes(root, new Consumer<TimedVcsCommit>() {
            @Override
            public void consume(@NotNull TimedVcsCommit commit) {
              graphCommits.add(compactCommit(commit, root));
            }
          });
          logInfo.put(root, graphCommits);
          logInfo.put(root, data.getRefs());
          myUserRegistry.addUsers(data.getUsers());
          sw.rootCompleted(root);
        }
      }.iterate(myProviders);
      myUserRegistry.flush();
      sw.report();
      return logInfo;
    }
  }

  private static class RefreshRequest {
    private static final RefreshRequest RELOAD_ALL = new RefreshRequest(Collections.<VirtualFile>emptyList()) {
      @Override
      public String toString() {
        return "RELOAD_ALL";
      }
    };
    private final Collection<VirtualFile> rootsToRefresh;

    RefreshRequest(@NotNull Collection<VirtualFile> rootsToRefresh) {
      this.rootsToRefresh = rootsToRefresh;
    }

    @Override
    public String toString() {
      return "{" + rootsToRefresh + "}";
    }
  }

  private static abstract class ProviderIterator {
    abstract void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException;

    final void iterate(@NotNull Map<VirtualFile, VcsLogProvider> providers) throws VcsException {
      for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
        each(entry.getKey(), entry.getValue());
      }
    }
  }

  private static class CommitCountRequirements implements VcsLogProvider.Requirements {
    private final int myCommitCount;

    public CommitCountRequirements(int commitCount) {
      myCommitCount = commitCount;
    }

    @Override
    public int getCommitCount() {
      return myCommitCount;
    }

    @NotNull
    Map<VirtualFile, VcsLogProvider.Requirements> asMap(@NotNull Collection<VirtualFile> roots) {
      return ContainerUtil.map2Map(roots, new Function<VirtualFile, Pair<VirtualFile, VcsLogProvider.Requirements>>() {
        @Override
        public Pair<VirtualFile, VcsLogProvider.Requirements> fun(VirtualFile root) {
          return Pair.<VirtualFile, VcsLogProvider.Requirements>create(root, CommitCountRequirements.this);
        }
      });
    }
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  private static class LogInfo {
    private final Map<VirtualFile, Set<VcsRef>> myRefs = ContainerUtil.newHashMap();
    private final Map<VirtualFile, List<GraphCommit<Integer>>> myCommits = ContainerUtil.newHashMap();

    void put(@NotNull VirtualFile root, @NotNull List<GraphCommit<Integer>> commits) {
      myCommits.put(root, commits);
    }

    void put(@NotNull VirtualFile root, @NotNull Set<VcsRef> refs) {
      myRefs.put(root, refs);
    }

    @NotNull
    Collection<List<GraphCommit<Integer>>> getCommits() {
      return myCommits.values();
    }

    List<GraphCommit<Integer>> getCommits(@NotNull VirtualFile root) {
      return myCommits.get(root);
    }

    @NotNull
    Map<VirtualFile, Set<VcsRef>> getRefs() {
      return myRefs;
    }

    public Set<VcsRef> getRefs(@NotNull VirtualFile root) {
      return myRefs.get(root);
    }
  }
}
