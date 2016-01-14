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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class VcsLogDataHolder implements Disposable, VcsLogDataProvider {

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
   * Cached details of the latest commits.
   * We store them separately from the cache of {@link DataGetter}, to make sure that they are always available,
   * which is important because these details will be constantly visible to the user,
   * thus it would be annoying to re-load them from VCS if the cache overflows.
   */
  @NotNull private final Map<Integer, VcsCommitMetadata> myTopCommitsDetailsCache = ContainerUtil.newConcurrentMap();

  private final VcsUserRegistryImpl myUserRegistry;

  private final VcsLogHashMapImpl myHashMap;
  private final ContainingBranchesGetter myContainingBranchesGetter;

  @NotNull private final VcsLogRefresher myRefresher;
  private final VcsLogFiltererImpl myFilterer;

  public VcsLogDataHolder(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                          @NotNull VcsLogSettings settings,
                          @NotNull VcsLogUiProperties uiProperties,
                          @NotNull Consumer<VisiblePack> visiblePackConsumer) {
    Disposer.register(parentDisposable, this);
    myProject = project;
    myLogProviders = logProviders;
    myDataLoaderQueue = new BackgroundTaskQueue(project, "Loading history...");
    mySettings = settings;
    myUserRegistry = (VcsUserRegistryImpl)ServiceManager.getService(project, VcsUserRegistry.class);

    try {
      myHashMap = new VcsLogHashMapImpl(myProject, logProviders);
    }
    catch (IOException e) {
      throw new RuntimeException(e); // TODO: show a message to the user & fallback to using in-memory Hashes
    }
    myMiniDetailsGetter = new MiniDetailsGetter(myHashMap, logProviders, myTopCommitsDetailsCache, this);
    myDetailsGetter = new CommitDetailsGetter(myHashMap, logProviders, this);
    myContainingBranchesGetter = new ContainingBranchesGetter(this, this);

    myFilterer = new VcsLogFiltererImpl(myProject, myLogProviders, myHashMap, myTopCommitsDetailsCache, myDetailsGetter,
                                        PermanentGraph.SortType.values()[uiProperties.getBekSortType()], visiblePackConsumer);

    myDataPackUpdateHandler = new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        myFilterer.onRefresh(dataPack);
      }
    };

    myRefresher =
      new VcsLogRefresherImpl(myProject, myHashMap, myLogProviders, myUserRegistry, myTopCommitsDetailsCache, myDataPackUpdateHandler,
                              new Consumer<Exception>() {
                                @Override
                                public void consume(Exception e) {
                                  if (!(e instanceof ProcessCanceledException)) {
                                    LOG.error(e);
                                  }
                                }
                              }, mySettings.getRecentCommitsCount());
  }

  @NotNull
  public VcsLogFilterer getFilterer() {
    return myFilterer;
  }

  @Override
  @NotNull
  public CommitId getCommitId(int commitIndex) {
    return myHashMap.getCommitId(commitIndex);
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    return myHashMap.getCommitIndex(hash, root);
  }

  @NotNull
  public VcsLogHashMapImpl getHashMap() {
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
    }, "Loading History...");
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
    myTopCommitsDetailsCache.clear();
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

  private void runInBackground(final ThrowableConsumer<ProgressIndicator, VcsException> task, final String title) {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
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
  public VcsCommitMetadata getTopCommitDetails(@NotNull Integer commitId) {
    return myTopCommitsDetailsCache.get(commitId);
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
  public VcsUserRegistryImpl getUserRegistry() {
    return myUserRegistry;
  }
}
