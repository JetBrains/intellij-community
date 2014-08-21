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
package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
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
  @NotNull private final Map<Hash, VcsCommitMetadata> myTopCommitsDetailsCache;
  @NotNull private final Consumer<Exception> myExceptionHandler;
  private final int myRecentCommitCount;

  @NotNull private final SingleTaskController<RefreshRequest, DataPack> mySingleTaskController;

  @NotNull private DataPack myDataPack = EmptyDataPack.getInstance();

  public VcsLogRefresherImpl(@NotNull final Project project,
                             @NotNull VcsLogHashMap hashMap,
                             @NotNull Map<VirtualFile, VcsLogProvider> providers,
                             @NotNull final VcsUserRegistryImpl userRegistry,
                             @NotNull Map<Hash, VcsCommitMetadata> topCommitsDetailsCache,
                             @NotNull final Consumer<DataPack> dataPackUpdateHandler,
                             @NotNull Consumer<Exception> exceptionHandler, int recentCommitsCount) {
    myProject = project;
    myHashMap = hashMap;
    myProviders = providers;
    myUserRegistry = userRegistry;
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myExceptionHandler = exceptionHandler;
    myRecentCommitCount = recentCommitsCount;

    Consumer<DataPack> dataPackUpdater = new Consumer<DataPack>() {
      @Override
      public void consume(@NotNull DataPack dataPack) {
        myDataPack = dataPack;
        dataPackUpdateHandler.consume(dataPack);
      }
    };
    mySingleTaskController = new SingleTaskController<RefreshRequest, DataPack>(dataPackUpdater) {
      @Override
      protected void startNewBackgroundTask() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            ProgressManagerImpl.runProcessWithProgressAsynchronously(new MyRefreshTask(myDataPack));
          }
        });
      }
    };
  }

  @NotNull
  @Override
  public DataPack readFirstBlock() {
    try {
      Map<VirtualFile, Collection<VcsRef>> refs = loadRefsFromVcs(myProviders);
      Set<VirtualFile> roots = myProviders.keySet();
      Map<VirtualFile, VcsLogProvider.Requirements> requirements = prepareSimpleRequirements(roots, myRecentCommitCount);
      Map<VirtualFile, List<? extends GraphCommit<Integer>>> commits = loadRecentCommitsFromVcs(myProviders, requirements,
                                                                                                myUserRegistry, myTopCommitsDetailsCache,
                                                                                                myHashMap);
      List<? extends GraphCommit<Integer>> compoundLog = compound(commits.values());
      DataPack dataPack = DataPack.build(compoundLog, new RefsModel(refs, myHashMap.asIndexGetter()),
                                         myHashMap.asIndexGetter(), myHashMap.asHashGetter(), myProviders, false);
      mySingleTaskController.request(RefreshRequest.RELOAD_ALL); // build/rebuild the full log in bg
      return dataPack;
    }
    catch (VcsException e) {
      myExceptionHandler.consume(e);
      return EmptyDataPack.getInstance();
    }
  }

  @Override
  public void refresh(@NotNull Collection<VirtualFile> rootsToRefresh) {
    if (!rootsToRefresh.isEmpty()) {
      mySingleTaskController.request(new RefreshRequest(rootsToRefresh));
    }
  }

  @NotNull
  private static Map<VirtualFile, VcsLogProvider.Requirements> prepareSimpleRequirements(@NotNull Collection<VirtualFile> roots,
                                                                                         final int commitCount) {
    final VcsLogProvider.Requirements requirements = new VcsLogProvider.Requirements() {
      @Override
      public int getCommitCount() {
        return commitCount;
      }
    };
    return ContainerUtil.map2Map(roots, new Function<VirtualFile, Pair<VirtualFile, VcsLogProvider.Requirements>>() {
      @Override
      public Pair<VirtualFile, VcsLogProvider.Requirements> fun(VirtualFile file) {
        return Pair.create(file, requirements);
      }
    });
  }

  @NotNull
  private static Map<VirtualFile, Collection<VcsRef>> loadRefsFromVcs(@NotNull Map<VirtualFile, VcsLogProvider> providers)
    throws VcsException {
    final StopWatch sw = StopWatch.start("loading refs");
    final Map<VirtualFile, Collection<VcsRef>> refs = ContainerUtil.newHashMap();
    new ProviderIterator() {
      @Override
      void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
        refs.put(root, provider.readAllRefs(root));
        sw.rootCompleted(root);
      }
    }.iterate(providers);
    sw.report();
    return refs;
  }

  @NotNull
  private static Map<VirtualFile, List<? extends GraphCommit<Integer>>> loadRecentCommitsFromVcs(
    @NotNull Map<VirtualFile, VcsLogProvider> providers,
    @NotNull final Map<VirtualFile, VcsLogProvider.Requirements> requirements,
    @NotNull final VcsUserRegistryImpl userRegistry,
    @NotNull final Map<Hash, VcsCommitMetadata> topCommitsDetailsCache,
    @NotNull final VcsLogHashMap hashMap) throws VcsException
  {
    final StopWatch sw = StopWatch.start("loading commits");
    final Map<VirtualFile, List<? extends GraphCommit<Integer>>> commits = ContainerUtil.newHashMap();
    new ProviderIterator() {
      @Override
      public void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
        List<? extends VcsCommitMetadata> metadatas = provider.readFirstBlock(root, requirements.get(root));
        storeUsersAndDetails(metadatas, userRegistry, topCommitsDetailsCache);
        commits.put(root, compactCommits(metadatas, hashMap));
        sw.rootCompleted(root);
      }
    }.iterate(providers);
    userRegistry.flush();
    sw.report();
    return commits;
  }

  /**
   * Compounds logs from different repositories into a single multi-repository log.
   */
  @NotNull
  private static List<? extends GraphCommit<Integer>> compound(@NotNull Collection<List<? extends GraphCommit<Integer>>> commits) {
    StopWatch sw = StopWatch.start("multi-repo join");
    List<? extends GraphCommit<Integer>> joined = new VcsLogMultiRepoJoiner<Integer>().join(commits);
    sw.report();
    return joined;
  }

  @NotNull
  private static List<GraphCommit<Integer>> compactCommits(@NotNull List<? extends TimedVcsCommit> commits,
                                                           @NotNull final VcsLogHashMap hashMap) {
    StopWatch sw = StopWatch.start("compacting commits");
    List<GraphCommit<Integer>> map = ContainerUtil.map(commits, new Function<TimedVcsCommit, GraphCommit<Integer>>() {
      @NotNull
      @Override
      public GraphCommit<Integer> fun(@NotNull TimedVcsCommit commit) {
        return compactCommit(commit, hashMap);
      }
    });
    hashMap.flush();
    sw.report();
    return map;
  }

  @NotNull
  private static GraphCommitImpl<Integer> compactCommit(@NotNull TimedVcsCommit commit, @NotNull VcsLogHashMap hashMap) {
    return new GraphCommitImpl<Integer>(hashMap.getCommitIndex(commit.getId()),
                                        ContainerUtil.map(commit.getParents(), hashMap.asIndexGetter()), commit.getTimestamp());
  }

  private static void storeUsersAndDetails(@NotNull List<? extends VcsCommitMetadata> metadatas, @NotNull VcsUserRegistryImpl userRegistry,
                                           @NotNull Map<Hash, VcsCommitMetadata> topCommitsDetailsCache) {
    for (VcsCommitMetadata detail : metadatas) {
      userRegistry.addUser(detail.getAuthor());
      userRegistry.addUser(detail.getCommitter());
      topCommitsDetailsCache.put(detail.getId(), detail);
    }
  }

  private class MyRefreshTask extends Task.Backgroundable {

    @NotNull private DataPack myCurrentDataPack;

    // collects loaded info from different roots, which refresh was requested consecutively within a single task
    private final Map<VirtualFile, LogAndRefs> myLoadedInfos = ContainerUtil.newHashMap();

    private class LogAndRefs {
      List<? extends GraphCommit<Integer>> log;
      Collection<VcsRef> refs;
      LogAndRefs(Collection<VcsRef> refs, List<? extends GraphCommit<Integer>> commits) {
        this.refs = refs;
        this.log = commits;
      }
    }

    MyRefreshTask(@NotNull DataPack currentDataPack) {
      super(VcsLogRefresherImpl.this.myProject, "Refreshing history...", false);
      myCurrentDataPack = currentDataPack;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      DataPack dataPack = myCurrentDataPack;
      while (true) {
        List<RefreshRequest> requests = mySingleTaskController.popRequests();
        Collection<VirtualFile> rootsToRefresh = getRootsToRefresh(requests);
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
          myCurrentDataPack = EmptyDataPack.getInstance();
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
      Map<VirtualFile, Collection<VcsRef>> currentRefs = myCurrentDataPack.getRefsModel().getAllRefsByRoot();
      try {
        if (permanentGraph != null) {
          int commitCount = myRecentCommitCount;
          for (int attempt = 0; attempt <= 1; attempt++) {
            loadLogAndRefs(roots, currentRefs, commitCount);
            List<? extends GraphCommit<Integer>> compoundLog = compoundLoadedLogs(myLoadedInfos.values());
            Map<VirtualFile, Collection<VcsRef>> allNewRefs = getAllNewRefs(myLoadedInfos, currentRefs);
            List<GraphCommit<Integer>> joinedFullLog = join(compoundLog, permanentGraph.getAllCommits(), currentRefs, allNewRefs);
            if (joinedFullLog == null) {
              commitCount *= 5;
            }
            else {
              return DataPack.build(joinedFullLog, new RefsModel(allNewRefs, myHashMap.asIndexGetter()),
                                    myHashMap.asIndexGetter(), myHashMap.asHashGetter(), myProviders, true);
            }
          }
          // couldn't join => need to reload everything; if 5000 commits is still not enough, it's worth reporting:
          LOG.error("Couldn't join " + commitCount + " recent commits to the log (" + permanentGraph.getAllCommits().size() + " commits)",
                    new Attachment("recent_commits", toLogString(myLoadedInfos)));
        }

        Pair<PermanentGraph<Integer>, Map<VirtualFile, Collection<VcsRef>>> fullLogAndRefs = loadFullLog();
        return DataPack.build(fullLogAndRefs.first, myProviders, new RefsModel(fullLogAndRefs.second, myHashMap.asIndexGetter()), true);
      }
      catch (Exception e) {
        myExceptionHandler.consume(e);
        return EmptyDataPack.getInstance();
      }
      finally {
        sw.report();
      }
    }

    private String toLogString(Map<VirtualFile, LogAndRefs> infos) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<VirtualFile, LogAndRefs> entry : infos.entrySet()) {
        sb.append(entry.getKey().getName());
        sb.append(" LOG:\n");
        sb.append(StringUtil.join(entry.getValue().log, new Function<GraphCommit<Integer>, String>() {
          @Override
          public String fun(GraphCommit<Integer> commit) {
            return commit.getId() + "<-" + StringUtil.join(commit.getParents(), ",");
          }
        }, "\n"));
        sb.append("\nREFS:\n");
        sb.append(StringUtil.join(entry.getValue().refs, new Function<VcsRef, String>() {
          @Override
          public String fun(VcsRef ref) {
            return ref.getName() + "(" + myHashMap.getCommitIndex(ref.getCommitHash()) + ")";
          }
        }, ","));
      }
      return sb.toString();
    }

    @NotNull
    private List<? extends GraphCommit<Integer>> compoundLoadedLogs(@NotNull Collection<LogAndRefs> logsAndRefs) {
      return compound(ContainerUtil.map(logsAndRefs, new Function<LogAndRefs, List<? extends GraphCommit<Integer>>>() {
        @Override
        public List<? extends GraphCommit<Integer>> fun(LogAndRefs refs) {
          return refs.log;
        }
      }));
    }

    @NotNull
    private Map<VirtualFile, Collection<VcsRef>> getAllNewRefs(@NotNull Map<VirtualFile, LogAndRefs> newInfo,
                                                               @NotNull Map<VirtualFile, Collection<VcsRef>> previousRefs) {
      Map<VirtualFile, Collection<VcsRef>> result = ContainerUtil.newHashMap();
      for (VirtualFile root : previousRefs.keySet()) {
        result.put(root, newInfo.containsKey(root) ? newInfo.get(root).refs : previousRefs.get(root));
      }
      return result;
    }

    private void loadLogAndRefs(@NotNull Collection<VirtualFile> roots, @NotNull Map<VirtualFile, Collection<VcsRef>> prevRefs,
                                int commitCount) throws VcsException {
      Map<VirtualFile, VcsLogProvider> providers = getProviders(roots);
      Map<VirtualFile, Collection<VcsRef>> refs = loadRefsFromVcs(providers);
      Map<VirtualFile, VcsLogProvider.Requirements> requirements = prepareRequirements(roots, commitCount, prevRefs, refs);
      Map<VirtualFile, List<? extends GraphCommit<Integer>>> commits = loadRecentCommitsFromVcs(providers, requirements,
                                                                                                myUserRegistry, myTopCommitsDetailsCache,
                                                                                                myHashMap);
      for (VirtualFile root : roots) {
        myLoadedInfos.put(root, new LogAndRefs(refs.get(root), commits.get(root)));
      }
    }

    @NotNull
    private Map<VirtualFile, VcsLogProvider.Requirements> prepareRequirements(@NotNull Collection<VirtualFile> roots,
                                                                              int commitCount,
                                                                              @NotNull Map<VirtualFile, Collection<VcsRef>> prevRefs,
                                                                              @NotNull Map<VirtualFile, Collection<VcsRef>> newRefs) {
      Map<VirtualFile, VcsLogProvider.Requirements> requirements = ContainerUtil.newHashMap();
      for (VirtualFile root : roots) {
        requirements.put(root, new RequirementsImpl(commitCount, true, getRefsForRoot(prevRefs, root), getRefsForRoot(newRefs, root)));
      }
      return requirements;
    }

    @NotNull
    private Set<VcsRef> getRefsForRoot(@NotNull Map<VirtualFile, Collection<VcsRef>> map, @NotNull VirtualFile root) {
      Collection<VcsRef> refs = map.get(root);
      return refs == null ? Collections.<VcsRef>emptySet() : new HashSet<VcsRef>(refs);
    }

    @NotNull
    private Map<VirtualFile, VcsLogProvider> getProviders(@NotNull Collection<VirtualFile> roots) {
      Map<VirtualFile, VcsLogProvider> providers = ContainerUtil.newHashMap();
      for (VirtualFile root : roots) {
        providers.put(root, myProviders.get(root));
      }
      return providers;
    }

    @Nullable
    private List<GraphCommit<Integer>> join(@NotNull List<? extends GraphCommit<Integer>> recentCommits, @NotNull List<GraphCommit<Integer>> fullLog,
                                            @NotNull Map<VirtualFile, Collection<VcsRef>> previousRefs,
                                            @NotNull Map<VirtualFile, Collection<VcsRef>> newRefs) {
      StopWatch sw = StopWatch.start("joining new commits");
      Function<VcsRef, Integer> ref2Int = new Function<VcsRef, Integer>() {
        @NotNull
        @Override
        public Integer fun(@NotNull VcsRef ref) {
          return myHashMap.getCommitIndex(ref.getCommitHash());
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
        LOG.info(e);
      }
      catch (IllegalStateException e) {
        LOG.error(e);
      }
      return null;
    }

    @NotNull
    private Pair<PermanentGraph<Integer>, Map<VirtualFile, Collection<VcsRef>>> loadFullLog() throws VcsException {
      StopWatch sw = StopWatch.start("full log reload");
      Collection<List<? extends GraphCommit<Integer>>> commits = readFullLogFromVcs();
      List<? extends GraphCommit<Integer>> graphCommits = compound(commits);
      Map<VirtualFile, Collection<VcsRef>> refMap = loadRefsFromVcs(myProviders);
      PermanentGraph<Integer> permanentGraph = DataPack.buildPermanentGraph(graphCommits, new RefsModel(refMap, myHashMap.asIndexGetter()),
                                                                            myHashMap.asIndexGetter(),
                                                                            myHashMap.asHashGetter(), myProviders);
      sw.report();
      return Pair.create(permanentGraph, refMap);
    }

    @NotNull
    private Collection<List<? extends GraphCommit<Integer>>> readFullLogFromVcs() throws VcsException {
      final StopWatch sw = StopWatch.start("read full log from VCS");
      final Collection<List<? extends GraphCommit<Integer>>> logs = ContainerUtil.newArrayList();
      new ProviderIterator() {
        @Override
        void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
          final List<GraphCommit<Integer>> graphCommits = ContainerUtil.newArrayList();
          provider.readAllHashes(root, new Consumer<VcsUser>() {
            @Override
            public void consume(@NotNull VcsUser user) {
              myUserRegistry.addUser(user);
            }
          }, new Consumer<TimedVcsCommit>() {
            @Override
            public void consume(TimedVcsCommit commit) {
              graphCommits.add(compactCommit(commit, myHashMap));
            }
          });
          logs.add(graphCommits);
          sw.rootCompleted(root);
        }
      }.iterate(myProviders);
      myUserRegistry.flush();
      sw.report();
      return logs;
    }
  }

  private static class RefreshRequest {
    private static RefreshRequest RELOAD_ALL = new RefreshRequest(Collections.<VirtualFile>emptyList());
    @NotNull private final Collection<VirtualFile> rootsToRefresh;

    RefreshRequest(@NotNull Collection<VirtualFile> rootsToRefresh) {
      this.rootsToRefresh = rootsToRefresh;
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
}
