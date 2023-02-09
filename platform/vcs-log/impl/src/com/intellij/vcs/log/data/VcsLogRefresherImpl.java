// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphCommitImpl;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.RequirementsImpl;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.intellij.diagnostic.telemetry.TraceKt.computeWithSpan;
import static com.intellij.diagnostic.telemetry.TraceUtil.computeWithSpanThrows;
import static com.intellij.diagnostic.telemetry.TraceUtil.runWithSpanThrows;

public class VcsLogRefresherImpl implements VcsLogRefresher, Disposable {

  private static final Logger LOG = Logger.getInstance(VcsLogRefresherImpl.class);


  private final @NotNull Project myProject;
  private final @NotNull VcsLogStorage myStorage;
  private final @NotNull Map<VirtualFile, VcsLogProvider> myProviders;
  private final @NotNull VcsUserRegistryImpl myUserRegistry;
  private final @NotNull VcsLogModifiableIndex myIndex;
  private final @NotNull TopCommitsCache myTopCommitsDetailsCache;
  private final @NotNull VcsLogProgress myProgress;

  private final int myRecentCommitCount;

  private final @NotNull SingleTaskController<RefreshRequest, DataPack> mySingleTaskController;

  private volatile @NotNull DataPack myDataPack = DataPack.EMPTY;

  private final @NotNull Tracer myTracer = TraceManager.INSTANCE.getTracer("vcs");

  public VcsLogRefresherImpl(@NotNull Project project,
                             @NotNull VcsLogStorage storage,
                             @NotNull Map<VirtualFile, VcsLogProvider> providers,
                             @NotNull VcsUserRegistryImpl userRegistry,
                             @NotNull VcsLogModifiableIndex index,
                             @NotNull VcsLogProgress progress,
                             @NotNull TopCommitsCache topCommitsDetailsCache,
                             @NotNull Consumer<? super DataPack> dataPackUpdateHandler,
                             int recentCommitsCount) {
    myProject = project;
    myStorage = storage;
    myProviders = providers;
    myUserRegistry = userRegistry;
    myIndex = index;
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myRecentCommitCount = recentCommitsCount;
    myProgress = progress;

    mySingleTaskController = new SingleTaskController<>("permanent", dataPack -> {
      myDataPack = dataPack;
      dataPackUpdateHandler.consume(dataPack);
    }, this) {
      @Override
      protected @NotNull SingleTask startNewBackgroundTask() {
        return VcsLogRefresherImpl.this.startNewBackgroundTask(new MyRefreshTask(myDataPack));
      }
    };
  }

  protected SingleTaskController.SingleTask startNewBackgroundTask(final @NotNull Task.Backgroundable refreshTask) {
    LOG.debug("Starting a background task...");
    ProgressIndicator indicator = myProgress.createProgressIndicator(VcsLogData.DATA_PACK_REFRESH);
    Future<?> future = ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(refreshTask, indicator,
                                                                                                                 null);
    return new SingleTaskController.SingleTaskImpl(future, indicator);
  }

  @Override
  public @NotNull DataPack getCurrentDataPack() {
    return myDataPack;
  }

  @Override
  public void readFirstBlock() {
    try {
      LogInfo data = loadRecentData(new CommitCountRequirements(myRecentCommitCount).asMap(myProviders.keySet()));
      Collection<List<GraphCommit<Integer>>> commits = data.getCommits();
      Map<VirtualFile, CompressedRefs> refs = data.getRefs();
      List<GraphCommit<Integer>> compoundList = multiRepoJoin(commits);
      compoundList = ContainerUtil.getFirstItems(compoundList, myRecentCommitCount);
      myDataPack = DataPack.build(compoundList, refs, myProviders, myStorage, false);
      mySingleTaskController.request(RefreshRequest.RELOAD_ALL); // build/rebuild the full log in background
    }
    catch (VcsException e) {
      LOG.info(e);
      myDataPack = new DataPack.ErrorDataPack(e);
    }
  }

  private @NotNull LogInfo loadRecentData(final @NotNull Map<VirtualFile, VcsLogProvider.Requirements> requirements) throws VcsException {
    return computeWithSpanThrows(myTracer, "loading commits", __ -> {
      final LogInfo logInfo = new LogInfo(myStorage);
      new ProviderIterator() {
        @Override
        public void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
          runWithSpanThrows(myTracer, "loading commits", spanForRoot -> {
            spanForRoot.setAttribute("rootName", root.getName());
            VcsLogProvider.DetailedLogData data = provider.readFirstBlock(root, requirements.get(root));
            logInfo.put(root, compactCommits(data.getCommits(), root));
            logInfo.put(root, data.getRefs());
            storeUsersAndDetails(data.getCommits());
          });
        }
      }.iterate(getProvidersForRoots(requirements.keySet()));
      myUserRegistry.flush();
      myIndex.scheduleIndex(false);
      return logInfo;
    });
  }

  private @NotNull Map<VirtualFile, VcsLogProvider> getProvidersForRoots(@NotNull Set<? extends VirtualFile> roots) {
    return ContainerUtil.map2Map(roots, root -> Pair.create(root, myProviders.get(root)));
  }

  @Override
  public void refresh(@NotNull Collection<VirtualFile> rootsToRefresh) {
    if (!rootsToRefresh.isEmpty()) {
      mySingleTaskController.request(new RefreshRequest(rootsToRefresh));
    }
  }

  private static @NotNull <T extends GraphCommit<Integer>> List<T> multiRepoJoin(@NotNull Collection<? extends List<T>> commits) {
    Span span = TraceManager.INSTANCE.getTracer("vcs").spanBuilder("multi-repo join").startSpan();
    List<T> joined = new VcsLogMultiRepoJoiner<Integer, T>().join(commits);
    span.end();
    return joined;
  }

  private @NotNull List<GraphCommit<Integer>> compactCommits(@NotNull List<? extends TimedVcsCommit> commits, final @NotNull VirtualFile root) {
    return computeWithSpan(myTracer, "compacting commits", (span -> {
      List<GraphCommit<Integer>> map = ContainerUtil.map(commits, commit -> compactCommit(commit, root));
      myStorage.flush();
      return map;
    }));
  }

  private @NotNull GraphCommit<Integer> compactCommit(@NotNull TimedVcsCommit commit, final @NotNull VirtualFile root) {
    List<Integer> parents = ContainerUtil.map(commit.getParents(),
                                              (NotNullFunction<Hash, Integer>)hash -> myStorage.getCommitIndex(hash, root));
    int index = myStorage.getCommitIndex(commit.getId(), root);
    myIndex.markForIndexing(index, root);
    return GraphCommitImpl.createIntCommit(index, parents, commit.getTimestamp());
  }

  private void storeUsersAndDetails(@NotNull List<? extends VcsCommitMetadata> metadatas) {
    for (VcsCommitMetadata detail : metadatas) {
      myUserRegistry.addUser(detail.getAuthor());
      myUserRegistry.addUser(detail.getCommitter());
    }
    myTopCommitsDetailsCache.storeDetails(metadatas);
  }

  public @NotNull VcsLogProgress getProgress() {
    return myProgress;
  }

  @Override
  public void dispose() {
  }

  private class MyRefreshTask extends Task.Backgroundable {

    private @NotNull DataPack myCurrentDataPack;
    private final @NotNull LogInfo myLoadedInfo = new LogInfo(myStorage);

    MyRefreshTask(@NotNull DataPack currentDataPack) {
      super(VcsLogRefresherImpl.this.myProject, VcsLogBundle.message("vcs.log.refreshing.process"), false);
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

        try {
          dataPack = doRefresh(rootsToRefresh);
        }
        catch (ProcessCanceledException e) {
          mySingleTaskController.taskCompleted(null);
          throw e;
        }
      }
    }

    private @NotNull Collection<VirtualFile> getRootsToRefresh(@NotNull List<? extends RefreshRequest> requests) {
      Collection<VirtualFile> rootsToRefresh = new ArrayList<>();
      for (RefreshRequest request : requests) {
        if (request == RefreshRequest.RELOAD_ALL) {
          myCurrentDataPack = DataPack.EMPTY;
          return myProviders.keySet();
        }
        rootsToRefresh.addAll(request.rootsToRefresh);
      }
      return rootsToRefresh;
    }

    private @NotNull DataPack doRefresh(@NotNull Collection<? extends VirtualFile> roots) {
      return computeWithSpanThrows(myTracer, "refresh", span -> {
        try {
          PermanentGraph<Integer> permanentGraph = myCurrentDataPack.isFull() ? myCurrentDataPack.getPermanentGraph() : null;
          Map<VirtualFile, CompressedRefs> currentRefs = myCurrentDataPack.getRefsModel().getAllRefsByRoot();
          Collection<VcsLogProvider> providers = ContainerUtil.filter(myProviders, roots::contains).values();
          boolean supportsIncrementalRefresh = ContainerUtil.all(providers, provider -> {
            return VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH.getOrDefault(provider);
          });
          if (permanentGraph != null && supportsIncrementalRefresh) {
            int commitCount = myRecentCommitCount;
            for (int attempt = 0; attempt <= 1; attempt++) {
              loadLogAndRefs(roots, currentRefs, commitCount);
              List<? extends GraphCommit<Integer>> compoundLog = multiRepoJoin(myLoadedInfo.getCommits());
              Map<VirtualFile, CompressedRefs> allNewRefs = getAllNewRefs(myLoadedInfo, currentRefs);
              List<? extends GraphCommit<Integer>> joinedFullLog = join(compoundLog, new ArrayList<>(permanentGraph.getAllCommits()),
                                                                        currentRefs, allNewRefs);
              if (joinedFullLog == null) {
                commitCount *= 5;
              }
              else {
                return DataPack.build(joinedFullLog, allNewRefs, myProviders, myStorage, true);
              }
            }
            // couldn't join => need to reload everything; if 5000 commits is still not enough, it's worth reporting:
            LOG.info("Couldn't join " + commitCount / 5 + " recent commits to the log (" +
                     permanentGraph.getAllCommits().size() + " commits)");
          }

          return loadFullLog();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.info(e);
          return new DataPack.ErrorDataPack(e);
        }
      });
    }

    private @NotNull Map<VirtualFile, CompressedRefs> getAllNewRefs(@NotNull LogInfo newInfo,
                                                                    @NotNull Map<VirtualFile, CompressedRefs> previousRefs) {
      Map<VirtualFile, CompressedRefs> result = new HashMap<>();
      for (VirtualFile root : previousRefs.keySet()) {
        CompressedRefs newInfoRefs = newInfo.getRefs().get(root);
        result.put(root, newInfoRefs != null ? newInfoRefs : previousRefs.get(root));
      }
      return result;
    }

    private void loadLogAndRefs(@NotNull Collection<? extends VirtualFile> roots,
                                @NotNull Map<VirtualFile, CompressedRefs> prevRefs,
                                int commitCount) throws VcsException {
      LogInfo logInfo = loadRecentData(prepareRequirements(roots, commitCount, prevRefs));
      for (VirtualFile root : roots) {
        myLoadedInfo.put(root, logInfo.getCommits(root));
        myLoadedInfo.put(root, logInfo.getRefs().get(root));
      }
    }

    private @NotNull Map<VirtualFile, VcsLogProvider.Requirements> prepareRequirements(@NotNull Collection<? extends VirtualFile> roots,
                                                                                       int commitCount,
                                                                                       @NotNull Map<VirtualFile, CompressedRefs> prevRefs) {
      Map<VirtualFile, VcsLogProvider.Requirements> requirements = new HashMap<>();
      for (VirtualFile root : roots) {
        requirements.put(root, new RequirementsImpl(commitCount, true, prevRefs.get(root).getRefs()));
      }
      return requirements;
    }

    private @Nullable List<? extends GraphCommit<Integer>> join(@NotNull List<? extends GraphCommit<Integer>> recentCommits,
                                                                @NotNull List<? extends GraphCommit<Integer>> fullLog,
                                                                @NotNull Map<VirtualFile, CompressedRefs> previousRefs,
                                                                @NotNull Map<VirtualFile, CompressedRefs> newRefs) {
      if (fullLog.isEmpty()) return recentCommits;

      return computeWithSpan(myTracer, "joining new commits", span -> {
        Collection<Integer> prevRefIndices = previousRefs
          .values()
          .stream()
          .flatMap(refs -> refs.getCommits().stream())
          .collect(Collectors.toSet());

        Collection<Integer> newRefIndices = newRefs
          .values()
          .stream()
          .flatMap(refs -> refs.getCommits().stream())
          .collect(Collectors.toSet());

        try {
          return new VcsLogJoiner<Integer, GraphCommit<Integer>>().addCommits(fullLog, prevRefIndices,
                                                                              recentCommits,
                                                                              newRefIndices).first;
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
      });
    }

    private @NotNull DataPack loadFullLog() throws VcsException {
      return computeWithSpanThrows(myTracer, "full log reload", span -> {
        LogInfo logInfo = readFullLogFromVcs();
        List<? extends GraphCommit<Integer>> graphCommits = multiRepoJoin(logInfo.getCommits());
        return DataPack.build(graphCommits, logInfo.getRefs(), myProviders, myStorage, true);
      });
    }

    private @NotNull LogInfo readFullLogFromVcs() throws VcsException {
      return computeWithSpanThrows(myTracer, "read full log from VCS", span -> {
        final LogInfo logInfo = new LogInfo(myStorage);
        new ProviderIterator() {
          @Override
          void each(final @NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException {
            runWithSpanThrows(myTracer, "read full log from VCS for " + root.getName(), scopeSpan -> {
              final List<GraphCommit<Integer>> graphCommits = new ArrayList<>();
              VcsLogProvider.LogData data = provider.readAllHashes(root, commit -> graphCommits.add(compactCommit(commit, root)));
              logInfo.put(root, graphCommits);
              logInfo.put(root, data.getRefs());
              myUserRegistry.addUsers(data.getUsers());
            });
          }
        }.iterate(myProviders);
        myUserRegistry.flush();
        myIndex.scheduleIndex(true);
        return logInfo;
      });
    }
  }

  private static class RefreshRequest {
    private static final RefreshRequest RELOAD_ALL = new RefreshRequest(Collections.emptyList()) {
      @Override
      public @NonNls String toString() {
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

  private abstract static class ProviderIterator {
    abstract void each(@NotNull VirtualFile root, @NotNull VcsLogProvider provider) throws VcsException;

    final void iterate(@NotNull Map<VirtualFile, VcsLogProvider> providers) throws VcsException {
      for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
        each(entry.getKey(), entry.getValue());
      }
    }
  }

  private static class CommitCountRequirements implements VcsLogProvider.Requirements {
    private final int myCommitCount;

    CommitCountRequirements(int commitCount) {
      myCommitCount = commitCount;
    }

    @Override
    public int getCommitCount() {
      return myCommitCount;
    }

    @NotNull
    Map<VirtualFile, VcsLogProvider.Requirements> asMap(@NotNull Collection<? extends VirtualFile> roots) {
      return ContainerUtil.map2Map(roots, root -> Pair.create(root, this));
    }
  }

  private static class LogInfo {
    private final VcsLogStorage myStorage;
    private final Map<VirtualFile, CompressedRefs> myRefs = new HashMap<>();
    private final Map<VirtualFile, List<GraphCommit<Integer>>> myCommits = new HashMap<>();

    LogInfo(VcsLogStorage storage) {
      myStorage = storage;
    }

    void put(@NotNull VirtualFile root, @NotNull List<GraphCommit<Integer>> commits) {
      myCommits.put(root, commits);
    }

    void put(@NotNull VirtualFile root, @NotNull Set<VcsRef> refs) {
      myRefs.put(root, new CompressedRefs(refs, myStorage));
    }

    void put(@NotNull VirtualFile root, @NotNull CompressedRefs refs) {
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
    Map<VirtualFile, CompressedRefs> getRefs() {
      return myRefs;
    }
  }
}
