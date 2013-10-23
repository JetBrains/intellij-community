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
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>Holds the commit data loaded from the VCS, and is capable to refresh this data by
 * {@link VcsLogProvider#subscribeToRootRefreshEvents(Collection, VcsLogRefresher) events from the VCS}.</p>
 * <p>If refresh is in progress, {@link #getDataPack()} returns the previous data pack (possibly not actual anymore).
 *    When refresh completes, the data pack instance is updated. Refreshes are chained.</p>
 *
 * <p><b>Thread-safety:</b> the class is thread-safe:
 *   <ul>
 *     <li>All write-operations made to the log and data pack are made sequentially, from the same background queue;</li>
 *     <li>Once a refresh request is received, we read logs and refs from all providers, join refresh data, join repositories,
 *         and build the new data pack sequentially in a single thread. After that we substitute the instance of the LogData object.</li>
 *     <li>Whilst we are in the middle of this refresh process, anyone who requests the data pack (and possibly the log if this would
 *         be available in the future), will get the consistent previous version of it.</li>
 *   </ul></p>
 *
 * <p><b>Initialization:</b><ul>
 *    <li>Initially the first part of the log is loaded and is shown to the user.</li>
 *    <li>Right after that, the whole log is loaded in the background. We need the whole log for two reasons:
 *        we don't want to hang for a minute if user decides to go to an old commit or even scroll;
 *        we need the whole log to properly perform the optimized refresh procedure.</li>
 *    <li>Once the whole log information is loaded, we don't rebuild the graphical log, because users rarely need old commits while
 *        memory would be occupied and performance would suffer.</li>
 *    <li>If user requests a commit that is not displayed (by clicking on an old branch reference, by going to a hash,
 *        just by scrolling down to the end of the table), we take the whole log, build the graph and show to the user.</li>
 *    <li>Once the whole log was once requested, we never hide it after refresh, because the reverse could annoy the user
 *        if he was looking at old history when refresh happened.</li>
 *    </li></ul>
 * </p>
 *
 * <p><b>Refresh procedure:</b><ul>
 *    <li>When a refresh request comes, we ask {@link VcsLogProvider log providers} about the last N commits unordered (which
 *        adds a significant performance gain in the case of Git).</li>
 *    <li>Then we order and attach these commits to the log with the help of {@link VcsLogJoiner}.</li>
 *    <li>In the multiple repository case logs from different providers are joined together to a single log.</li>
 *    <li>If user was looking at the top part of the log, the log is rebuilt, and new top of the log is shown to the user.
 *        If, however, he was looking at the whole log, the data pack for the whole log is rebuilt and shown to the user.
 *    </li></ul></p>
 *
 * TODO: error handling
 *
 * @author Kirill Likhodedov
 */
public class VcsLogDataHolder implements Disposable {

  public static final Topic<Runnable> REFRESH_COMPLETED = Topic.create("Vcs.Log.Completed", Runnable.class);

  private static final Logger LOG = Logger.getInstance(VcsLogDataHolder.class);

  @NotNull private final Project myProject;
  @NotNull private final VcsLogObjectsFactory myFactory;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final BackgroundTaskQueue myDataLoaderQueue;
  @NotNull private final MiniDetailsGetter myMiniDetailsGetter;
  @NotNull private final CommitDetailsGetter myDetailsGetter;
  @NotNull private final VcsLogJoiner myLogJoiner;
  @NotNull private final VcsLogMultiRepoJoiner myMultiRepoJoiner;
  @NotNull private final VcsLogSettings mySettings;

  /**
   * Encapsulates all information about the log, which can be accessed by external clients.
   * When something changes in the log (on refresh, for example), the whole object is replaced with the new one;
   * all write-access operations with are performed from the myDataLoaderQueue;
   * therefore it is guaranteed that myData doesn't change within a single runInBackground() task.
   *
   * The object is null only before the first refresh, the whole component is not available before the first refresh at all.
   */
  private volatile LogData myLogData;

  /**
   * Indicates if user wants the whole log graph to be shown.
   * Initially we show only the top of the log (even after we've loaded the whole log structure) for performance reasons.
   * However, if user once navigates to some old commit, we build the whole graph and show it until the next full refresh or project reload.
   */
  private volatile boolean myFullLogShowing;

  /**
   * Cached details of the latest commits.
   * We store them separately from the cache of {@link DataGetter}, to make sure that they are always available,
   * which is important because these details will be constantly visible to the user,
   * thus it would be annoying to re-load them from VCS if the cache overflows.
   */
  @NotNull private final Map<Hash, VcsFullCommitDetails> myTopCommitsDetailsCache = ContainerUtil.newConcurrentMap();

  public VcsLogDataHolder(@NotNull Project project, @NotNull VcsLogObjectsFactory logObjectsFactory,
                          @NotNull Map<VirtualFile, VcsLogProvider> logProviders, @NotNull VcsLogSettings settings) {
    myProject = project;
    myLogProviders = logProviders;
    myDataLoaderQueue = new BackgroundTaskQueue(project, "Loading history...");
    myMiniDetailsGetter = new MiniDetailsGetter(this, logProviders);
    myDetailsGetter = new CommitDetailsGetter(this, logProviders);
    myLogJoiner = new VcsLogJoiner();
    myMultiRepoJoiner = new VcsLogMultiRepoJoiner();
    myFactory = logObjectsFactory;
    mySettings = settings;
  }

  /**
   * Initializes the VcsLogDataHolder in background in the following sequence:
   * <ul>
   * <li>Loads the first part of the log with details.</li>
   * <li>Invokes the Consumer to initialize the UI with the initial data pack.</li>
   * <li>Loads the whole log in background. When completed, substitutes the data and tells the UI to refresh itself.</li>
   * </ul>
   *
   * @param settings
   * @param onInitialized This is called when the holder is initialized with the initial data received from the VCS.
   */
  public static void init(@NotNull final Project project, @NotNull VcsLogObjectsFactory logObjectsFactory,
                          @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                          @NotNull VcsLogSettings settings, @NotNull final Consumer<VcsLogDataHolder> onInitialized) {
    final VcsLogDataHolder dataHolder = new VcsLogDataHolder(project, logObjectsFactory, logProviders, settings);
    dataHolder.initialize(onInitialized);
  }

  private void initialize(@NotNull final Consumer<VcsLogDataHolder> onInitialized) {
    myDataLoaderQueue.clear();
    loadFirstPart(new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        onInitialized.consume(VcsLogDataHolder.this);
        // after first part is loaded and shown to the user, load the whole log in background
        loadAllLog();
      }
    }, true);
  }

  private void loadAllLog() {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        Map<VirtualFile, List<TimedVcsCommit>> logs = ContainerUtil.newHashMap();
        Map<VirtualFile, Collection<VcsRef>> refs = ContainerUtil.newHashMap();
        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          VirtualFile root = entry.getKey();
          VcsLogProvider logProvider = entry.getValue();
          logs.put(root, logProvider.readAllHashes(root));
          refs.put(root, logProvider.readAllRefs(root));
        }
        DataPack existingDataPack = myLogData.getDataPack();
        // keep existing data pack: we don't want to rebuild the graph,
        // we just make the whole log structure available for our cunning refresh procedure of if user requests the whole graph
        myLogData = new LogData(logs, refs, myLogData.getTopCommits(), existingDataPack, true);
      }
    });
  }

  public void showFullLog(@NotNull final Runnable onSuccess) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        if (myLogData.isFullLogReady()) { // TODO if not ready, wait for it
          List<TimedVcsCommit> compoundLog = myMultiRepoJoiner.join(myLogData.myLogsByRoot.values());
          DataPack fullDataPack = DataPack.build(compoundLog, myLogData.getAllRefs(), indicator);
          myLogData = new LogData(myLogData.getLogs(), myLogData.getRefs(), myLogData.getTopCommits(), fullDataPack, true);
          myFullLogShowing = true;
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              notifyAboutDataRefresh();
              onSuccess.run();
            }
          });
        }
      }
    });
  }

  /**
   * Loads the top part of the log and rebuilds the graph & log table.
   *
   * @param onSuccess this task is called {@link UIUtil#invokeAndWaitIfNeeded(Runnable) on the EDT} after loading and graph
   *                  building completes.
   * @param invalidateWholeLog if the whole log data should be invalidated and will be retrieved in onSuccess.
   */
  private void loadFirstPart(@NotNull final Consumer<DataPack> onSuccess, final boolean invalidateWholeLog) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        boolean initialization = myLogData == null; // the object is null before the first refresh
        boolean isFullLogReady = !initialization && myLogData.isFullLogReady();
        // if we don't have the full log yet, the VcsLogJoiner won't work  => we need to fairly query the VCS
        boolean fairRefresh = invalidateWholeLog || !isFullLogReady;

        Map<VirtualFile, List<TimedVcsCommit>> logsToBuild = ContainerUtil.newHashMap();
        Map<VirtualFile, Collection<VcsRef>> refsByRoot = ContainerUtil.newHashMap();
        int topCommitCount = myLogData == null ? 0 : myLogData.getTopCommitsCount();

        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          VirtualFile root = entry.getKey();
          VcsLogProvider logProvider = entry.getValue();

          // read info from VCS
          List<? extends VcsFullCommitDetails> firstBlockDetails = logProvider.readFirstBlock(root, fairRefresh,
                                                                                              mySettings.getRecentCommitsCount());
          Collection<VcsRef> newRefs = logProvider.readAllRefs(root);
          storeTopCommitsDetailsInCache(firstBlockDetails);
          List<TimedVcsCommit> firstBlockCommits = getCommitsFromDetails(firstBlockDetails);

          // refresh
          List<TimedVcsCommit> refreshedLog;
          if (fairRefresh) {
            refreshedLog = firstBlockCommits;
            // in this case new commits won't be attached to the log, but will substitute existing ones.
            topCommitCount = firstBlockCommits.size();
          }
          else {
            Pair<List<TimedVcsCommit>, Integer> joinResult = myLogJoiner.addCommits(myLogData.getLog(root), myLogData.getRefs(root),
                                                                                    firstBlockCommits, newRefs);
            refreshedLog = joinResult.getFirst();
            int newCommitsCount = joinResult.getSecond();
            // the value can significantly increase if user keeps IDEA open for a long time, and frequently receives many new commits,
            // but it is expected: we can work with long logs. A limit can be added in future if this becomes a problem.
            topCommitCount += newCommitsCount;
          }

          logsToBuild.put(root, refreshedLog);
          refsByRoot.put(root, newRefs);
        }

        List<TimedVcsCommit> compoundLog = myMultiRepoJoiner.join(logsToBuild.values());
        List<TimedVcsCommit> topPartOfTheLog = compoundLog.subList(0, topCommitCount);

        List<TimedVcsCommit> logToBuild = myFullLogShowing ? compoundLog : topPartOfTheLog; // keep looking at the full log after refresh
        final DataPack dataPack = DataPack.build(logToBuild, collectAllRefs(refsByRoot), indicator);

        myLogData = new LogData(logsToBuild, refsByRoot, topPartOfTheLog, dataPack, isFullLogReady);

        handleOnSuccessInEdt(onSuccess, dataPack);
      }
    });
  }

  private static Collection<VcsRef> collectAllRefs(Map<VirtualFile, Collection<VcsRef>> refsByRoot) {
    Collection<VcsRef> allRefs = new ArrayList<VcsRef>();
    for (Collection<VcsRef> refs : refsByRoot.values()) {
      allRefs.addAll(refs);
    }
    return allRefs;
  }

  private static void handleOnSuccessInEdt(final Consumer<DataPack> onSuccess, final DataPack dataPack) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        onSuccess.consume(dataPack);
      }
    });
  }

  private void storeTopCommitsDetailsInCache(List<? extends VcsFullCommitDetails> firstBlockDetails) {
    // some commits may be no longer available (e.g. rewritten after rebase), but let them stay in the cache:
    // they won't occupy too much place, while checking & removing them is not easy.
    for (VcsFullCommitDetails detail : firstBlockDetails) {
      myTopCommitsDetailsCache.put(detail.getHash(), detail);
    }
  }

  private List<TimedVcsCommit> getCommitsFromDetails(List<? extends VcsFullCommitDetails> firstBlockDetails) {
    return ContainerUtil.map(firstBlockDetails, new Function<VcsFullCommitDetails, TimedVcsCommit>() {
      @Override
      public TimedVcsCommit fun(VcsFullCommitDetails details) {
        return myFactory.createTimedCommit(details.getHash(), details.getParents(), details.getAuthorTime());
      }
    });
  }

  private void runInBackground(final ThrowableConsumer<ProgressIndicator, VcsException> task) {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, "Loading history...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          task.consume(indicator);
        }
        catch (VcsException e) {
          throw new RuntimeException(e); // TODO
        }
      }
    });
  }

  private void refresh(@NotNull final Runnable onSuccess) {
    loadFirstPart(new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        onSuccess.run();
      }
    }, false);
  }

  /**
   * Makes the log perform complete refresh for all roots.
   * It fairly retrieves the data from the VCS and rebuilds the whole log.
   */
  public void refreshCompletely() {
    initialize(new Consumer<VcsLogDataHolder>() {
      @Override
      public void consume(VcsLogDataHolder holder) {
        notifyAboutDataRefresh();
      }
    });
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i. e. it can query VCS just for the part of the log.
   */
  public void refresh(@NotNull VirtualFile root) {
    // TODO refresh only the given root, not all roots
    refresh(new Runnable() {
      @Override
      public void run() {
        notifyAboutDataRefresh();
      }
    });
  }

  /**
   * Makes the log refresh only the reference labels for the given root.
   */
  public void refreshRefs(@NotNull VirtualFile root) {
    // TODO no need to query the VCS for commit & rebuild the whole log; just replace refs labels.
    refresh(root);
  }

  @NotNull
  public DataPack getDataPack() {
    return myLogData.getDataPack();
  }

  @Nullable
  public VcsFullCommitDetails getTopCommitDetails(@NotNull Hash hash) {
    return myTopCommitsDetailsCache.get(hash);
  }

  private void notifyAboutDataRefresh() {
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(REFRESH_COMPLETED).run();
    }
  }

  public CommitDetailsGetter getCommitDetailsGetter() {
    return myDetailsGetter;
  }

  @NotNull
  public MiniDetailsGetter getMiniDetailsGetter() {
    return myMiniDetailsGetter;
  }

  @Override
  public void dispose() {
    myLogData = null;
    myDataLoaderQueue.clear();
  }

  public boolean isFullLogReady() {
    return myLogData.isFullLogReady();
  }

  @NotNull
  public VcsLogProvider getLogProvider(@NotNull VirtualFile root) {
    return myLogProviders.get(root);
  }

  /**
   * Returns the ordered part of the recent commits, details of which are always stored in the cache.
   */
  @NotNull
  public Collection<TimedVcsCommit> getTopCommits() {
    return myLogData.getTopCommits();
  }

  /**
   * Returns details of recent commits ordered in the "right way" (as in the log).
   */
  @NotNull
  public Collection<VcsFullCommitDetails> getTopCommitDetails() {
    final Collection<TimedVcsCommit> topCommits = getTopCommits();
    return ContainerUtil.mapNotNull(topCommits, new Function<TimedVcsCommit, VcsFullCommitDetails>() {
      @Nullable
      @Override
      public VcsFullCommitDetails fun(TimedVcsCommit commit) {
        Hash hash = commit.getHash();
        VcsFullCommitDetails details = myTopCommitsDetailsCache.get(hash);
        if (details != null) {
          return details;
        }

        // shouldn't happen
        LOG.error("No details were stored for commit " + hash,
                  new Attachment("details_cache.txt", myTopCommitsDetailsCache.toString()),
                  new Attachment("top_commits.txt", topCommits.toString()));
        return null;
      }
    });
  }

  /**
   * Contains full logs per repository root & references per root.
   *
   * Until we have loaded the full log of the repository, we store the top part of the log which was loaded.
   * When we load the full structure, it is substituted.
   */
  private static class LogData {
    @NotNull private final Map<VirtualFile, List<TimedVcsCommit>> myLogsByRoot;
    @NotNull private final Map<VirtualFile, Collection<VcsRef>> myRefsByRoot;
    @NotNull private final List<TimedVcsCommit> myCompoundTopCommits;
    @NotNull private final DataPack myDataPack;
    private final boolean myFullLog;

    private LogData(@NotNull Map<VirtualFile, List<TimedVcsCommit>> logsByRoot,
                    @NotNull Map<VirtualFile, Collection<VcsRef>> refsByRoot, @NotNull List<TimedVcsCommit> compoundTopCommits,
                    @NotNull DataPack dataPack, boolean fullLog) {
      myLogsByRoot = logsByRoot;
      myRefsByRoot = refsByRoot;
      myCompoundTopCommits = compoundTopCommits;
      myDataPack = dataPack;
      myFullLog = fullLog;
    }

    @NotNull
    public List<TimedVcsCommit> getLog(@NotNull VirtualFile root) {
      return myLogsByRoot.get(root);
    }

    @NotNull
    public Collection<VcsRef> getRefs(@NotNull VirtualFile root) {
      return myRefsByRoot.get(root);
    }

    @NotNull
    public Collection<VcsRef> getAllRefs() {
      Collection<VcsRef> allRefs = new HashSet<VcsRef>();
      for (Collection<VcsRef> refs : myRefsByRoot.values()) {
        allRefs.addAll(refs);
      }
      return allRefs;
    }

    @NotNull
    public DataPack getDataPack() {
      return myDataPack;
    }

    public boolean isFullLogReady() {
      return myFullLog;
    }

    @NotNull
    public Map<VirtualFile,List<TimedVcsCommit>> getLogs() {
      return myLogsByRoot;
    }

    @NotNull
    public Map<VirtualFile, Collection<VcsRef>> getRefs() {
      return myRefsByRoot;
    }

    public int getTopCommitsCount() {
      return myCompoundTopCommits.size();
    }

    @NotNull
    public List<TimedVcsCommit> getTopCommits() {
      return myCompoundTopCommits;
    }
  }

}
