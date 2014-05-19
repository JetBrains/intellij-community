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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class VcsLogDataHolder implements Disposable, VcsLogDataProvider {

  public static final Topic<VcsLogRefreshListener> REFRESH_COMPLETED = Topic.create("Vcs.Log.Completed", VcsLogRefreshListener.class);

  private static final Logger LOG = Logger.getInstance(VcsLogDataHolder.class);

  @NotNull private final Project myProject;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final BackgroundTaskQueue myDataLoaderQueue;
  @NotNull private final MiniDetailsGetter myMiniDetailsGetter;
  @NotNull private final CommitDetailsGetter myDetailsGetter;
  @NotNull private final VcsLogSettings mySettings;

  /**
   * Current user name, as specified in the VCS settings.
   * It can be configured differently for different roots => store in a map.
   */
  private final Map<VirtualFile, VcsUser> myCurrentUser = ContainerUtil.newHashMap();
  private final Consumer<DataPack> myDataPackUpdateHandler;

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
  @NotNull private final Map<Hash, VcsCommitMetadata> myTopCommitsDetailsCache = ContainerUtil.newConcurrentMap();

  private final VcsUserRegistry myUserRegistry;

  private final VcsLogHashMap myHashMap;
  private final ContainingBranchesGetter myContainingBranchesGetter;

  @NotNull private final VcsLogRefresher myRefresher;

  public VcsLogDataHolder(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                          @NotNull VcsLogSettings settings, Consumer<DataPack> dataPackUpdateHandler) {
    Disposer.register(parentDisposable, this);
    myProject = project;
    myLogProviders = logProviders;
    myDataLoaderQueue = new BackgroundTaskQueue(project, "Loading history...");
    myMiniDetailsGetter = new MiniDetailsGetter(this, logProviders);
    myDetailsGetter = new CommitDetailsGetter(this, logProviders);
    mySettings = settings;
    myDataPackUpdateHandler = dataPackUpdateHandler;
    myUserRegistry = new VcsUserRegistry();

    try {
      myHashMap = new VcsLogHashMap(myProject);
    }
    catch (IOException e) {
      throw new RuntimeException(e); // TODO: show a message to the user & fallback to using in-memory Hashes
    }
    myContainingBranchesGetter = new ContainingBranchesGetter(project, this, this);

    myRefresher = new VcsLogRefresherImpl(myProject, myHashMap, myLogProviders, myUserRegistry, myTopCommitsDetailsCache,
                                          dataPackUpdateHandler, new Consumer<Exception>() {
      @Override
      public void consume(Exception e) {
        LOG.error(e);
      }
    }, mySettings.getRecentCommitsCount());
  }

  @Override
  @NotNull
  public Hash getHash(int commitIndex) {
    return myHashMap.getHash(commitIndex);
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash) {
    return myHashMap.getCommitIndex(hash);
  }

  @NotNull
  public VcsLogHashMap getHashMap() {
    return myHashMap;
  }

  public void initialize() {
    final StopWatch initSw = StopWatch.start("initialize");
    myDataLoaderQueue.clear();

    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        resetState();
        readCurrentUser();
        DataPack dataPack = myRefresher.readFirstBlock();
        myDataPackUpdateHandler.consume(dataPack);
        initSw.report();
      }
    }, "Loading recent history...");
  }

  private void readCurrentUser() {
    StopWatch sw = StopWatch.start("readCurrentUser");
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
      VirtualFile root = entry.getKey();
      try {
        VcsUser me = entry.getValue().getCurrentUser(root);
        if (me != null) {
          myCurrentUser.put(root, me);
        }
        else {
          LOG.info("Username not configured for root " + root);
        }
      }
      catch (VcsException e) {
        LOG.warn("Couldn't read the username from root " + root, e);
      }
    }
    sw.report();
  }

  private void resetState() {
    myFullLogShowing = false;
    myTopCommitsDetailsCache.clear();
  }

  /**
   * Show the full log tree to the user.
   * Initially only the top part of the log is shown to avoid memory and performance problems.
   * However, if user wants to navigate to something in the past, we rebuild the log and show it.
   * <p/>
   * Method returns immediately, log building is executed in the background.
   * <p/>
   * TODO: in most cases, users don't need to go to such deep past even if they need to go deeper than to 1000 most recent commits.
   * Therefore optimize: Add a hash parameter, and build only the necessary part of the log + some commits below.
   *
   * @param onSuccess Invoked in the EDT after the log DataPack is built.
   */
  public void showFullLog(@NotNull final Runnable onSuccess) {
    if (myFullLogShowing) {
      return;
    }

    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        if (myFullLogShowing) {
          return;
        }

        // TODO

        myFullLogShowing = true;
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!Disposer.isDisposed(VcsLogDataHolder.this)) {
              onSuccess.run();
            }
          }
        });
      }
    }, "Building full log...");
  }


  public boolean isFullLogShowing() {
    return myFullLogShowing;
  }

  @NotNull
  public Set<VcsUser> getAllUsers() {
    return myUserRegistry.getUsers();
  }

  @NotNull
  public Map<VirtualFile, VcsUser> getCurrentUser() {
    return myCurrentUser;
  }

  public boolean isMultiRoot() {
    return myLogProviders.size() > 1;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Collection<VirtualFile> getRoots() {
    return myLogProviders.keySet();
  }

  @NotNull
  public Collection<VcsLogProvider> getLogProviders() {
    return myLogProviders.values();
  }

  @NotNull
  public VcsLogSettings getSettings() {
    return mySettings;
  }

  public ContainingBranchesGetter getContainingBranchesGetter() {
    return myContainingBranchesGetter;
  }

  @Nullable
  public Hash findHashByString(@NotNull String string) {
    final String pHash = string.toLowerCase();
    try {
      return myHashMap.findHash(new Condition<Hash>() {
        @Override
        public boolean value(@NotNull Hash hash) {
          return hash.toString().toLowerCase().startsWith(pHash);
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  void runInBackground(final ThrowableConsumer<ProgressIndicator, VcsException> task, final String title) {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, title) {
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

  /**
   * Makes the log perform complete refresh for all roots.
   * It fairly retrieves the data from the VCS and rebuilds the whole log.
   */
  public void refreshCompletely() {
    initialize();
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i. e. it can query VCS just for the part of the log.
   */
  public void refresh(@NotNull Collection<VirtualFile> roots) {
    myRefresher.refresh(roots);
  }

  @Nullable
  public VcsCommitMetadata getTopCommitDetails(@NotNull Hash hash) {
    return myTopCommitsDetailsCache.get(hash);
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
    myDataLoaderQueue.clear();
    resetState();
  }

  @NotNull
  public VcsLogProvider getLogProvider(@NotNull VirtualFile root) {
    return myLogProviders.get(root);
  }

  @NotNull
  public VcsUserRegistry getUserRegistry() {
    return myUserRegistry;
  }
}
