// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex;
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.impl.VcsLogCachesInvalidator;
import com.intellij.vcs.log.impl.VcsLogSharedSettings;
import com.intellij.vcs.log.util.PersistentUtil;
import com.intellij.vcs.log.util.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class VcsLogData implements Disposable, VcsLogDataProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogData.class);
  public static final int RECENT_COMMITS_COUNT = Registry.intValue("vcs.log.recent.commits.count");
  public static final VcsLogProgress.ProgressKey DATA_PACK_REFRESH = new VcsLogProgress.ProgressKey("data pack");

  @NotNull private final Project myProject;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final MiniDetailsGetter myMiniDetailsGetter;
  @NotNull private final CommitDetailsGetter myDetailsGetter;
  @NotNull private final CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  /**
   * Current user name, as specified in the VCS settings.
   * It can be configured differently for different roots => store in a map.
   */
  private final Map<VirtualFile, VcsUser> myCurrentUser = new ConcurrentHashMap<>();

  /**
   * Cached details of the latest commits.
   * We store them separately from the cache of {@link DataGetter}, to make sure that they are always available,
   * which is important because these details will be constantly visible to the user,
   * thus it would be annoying to re-load them from VCS if the cache overflows.
   */
  @NotNull private final TopCommitsCache myTopCommitsDetailsCache;
  @NotNull private final VcsUserRegistryImpl myUserRegistry;
  @NotNull private final VcsLogUserResolver myUserResolver;
  @NotNull private final VcsLogStorage myStorage;
  @NotNull private final ContainingBranchesGetter myContainingBranchesGetter;
  @NotNull private final VcsLogRefresherImpl myRefresher;
  @NotNull private final List<DataPackChangeListener> myDataPackChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;
  @NotNull private final VcsLogModifiableIndex myIndex;

  @NotNull private final Object myLock = new Object();
  @NotNull private State myState = State.CREATED;
  @Nullable private SingleTaskController.SingleTask myInitialization = null;

  public VcsLogData(@NotNull Project project,
                    @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                    @NotNull FatalErrorHandler fatalErrorsConsumer,
                    @NotNull Disposable parentDisposable) {
    myProject = project;
    myLogProviders = logProviders;
    myUserRegistry = (VcsUserRegistryImpl)project.getService(VcsUserRegistry.class);
    myFatalErrorsConsumer = fatalErrorsConsumer;

    VcsLogProgress progress = new VcsLogProgress(this);

    if (VcsLogCachesInvalidator.getInstance().isValid()) {
      myStorage = createStorage();
      if (VcsLogSharedSettings.isIndexSwitchedOn(myProject)) {
        myIndex = new VcsLogPersistentIndex(myProject, myStorage, progress, logProviders, myFatalErrorsConsumer, this);
      }
      else {
        LOG.info("Vcs log index is turned off for project " + myProject.getName());
        myIndex = new EmptyIndex();
      }
    }
    else {
      // this is not recoverable
      // restart won't help here
      // and can not shut down ide because of this
      // so use memory storage (probably leading to out of memory at some point) + no index

      LOG.error("Could not delete caches at " + PersistentUtil.LOG_CACHE);
      myFatalErrorsConsumer.displayFatalErrorMessage(VcsLogBundle.message("vcs.log.fatal.error.message", PersistentUtil.LOG_CACHE,
                                                                          ApplicationNamesInfo.getInstance().getFullProductName()));
      myStorage = new InMemoryStorage();
      myIndex = new EmptyIndex();
    }

    myTopCommitsDetailsCache = new TopCommitsCache(myStorage);
    myMiniDetailsGetter = new MiniDetailsGetter(myProject, myStorage, logProviders, myTopCommitsDetailsCache, myIndex, this);
    myDetailsGetter = new CommitDetailsGetter(myStorage, logProviders, this);

    myRefresher = new VcsLogRefresherImpl(myProject, myStorage, myLogProviders, myUserRegistry, myIndex, progress, myTopCommitsDetailsCache,
                                          this::fireDataPackChangeEvent, RECENT_COMMITS_COUNT);
    Disposer.register(this, myRefresher);

    myContainingBranchesGetter = new ContainingBranchesGetter(this, this);
    myUserResolver = new MyVcsLogUserResolver();

    Disposer.register(parentDisposable, this);
    Disposer.register(this, () -> {
      synchronized (myLock) {
        if (myInitialization != null) {
          myInitialization.cancel();
        }
      }
    });
    Disposer.register(this, myDisposableFlag);
  }

  @NotNull
  private VcsLogStorage createStorage() {
    VcsLogStorage hashMap;
    try {
      hashMap = new VcsLogStorageImpl(myProject, myLogProviders, myFatalErrorsConsumer, this);
    }
    catch (IOException e) {
      hashMap = new InMemoryStorage();
      LOG.error("Falling back to in-memory hashes", e);
    }
    return hashMap;
  }

  public void initialize() {
    synchronized (myLock) {
      if (myState.equals(State.CREATED)) {
        myState = State.INITIALIZED;
        StopWatch stopWatch = StopWatch.start("initialize");
        Task.Backgroundable backgroundable = new Task.Backgroundable(myProject,
                                                                     VcsLogBundle.message("vcs.log.initial.loading.process"),
                                                                     false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(true);
            resetState();
            readCurrentUser();
            DataPack dataPack = myRefresher.readFirstBlock();
            fireDataPackChangeEvent(dataPack);
            stopWatch.report();
          }

          @Override
          public void onCancel() {
            synchronized (myLock) {
              // Here be dragons:
              // VcsLogProgressManager can cancel us when it's getting disposed,
              // and we can also get cancelled by invalid git executable.
              // Since we do not know what's up, we just restore the state
              // and it is entirely possible to start another initialization after that.
              // Eventually, everything gets cancelled for good in VcsLogData.dispose.
              // But still.
              if (myState.equals(State.INITIALIZED)) {
                myState = State.CREATED;
                myInitialization = null;
              }
            }
          }

          @Override
          public void onThrowable(@NotNull Throwable error) {
            synchronized (myLock) {
              LOG.error(error);
              if (myState.equals(State.INITIALIZED)) {
                myState = State.CREATED;
                myInitialization = null;
              }
            }
          }

          @Override
          public void onSuccess() {
            synchronized (myLock) {
              if (myState.equals(State.INITIALIZED)) {
                myInitialization = null;
              }
            }
          }
        };
        CoreProgressManager manager = (CoreProgressManager)ProgressManager.getInstance();
        ProgressIndicator indicator = myRefresher.getProgress().createProgressIndicator(DATA_PACK_REFRESH);
        Future<?> future = manager.runProcessWithProgressAsynchronously(backgroundable, indicator, null);
        myInitialization = new SingleTaskController.SingleTaskImpl(future, indicator);
      }
    }
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

  private void fireDataPackChangeEvent(@NotNull final DataPack dataPack) {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (DataPackChangeListener listener : myDataPackChangeListeners) {
        listener.onDataPackChange(dataPack);
      }
    }, o -> myDisposableFlag.isDisposed());
  }

  public void addDataPackChangeListener(@NotNull final DataPackChangeListener listener) {
    myDataPackChangeListeners.add(listener);
  }

  public void removeDataPackChangeListener(@NotNull DataPackChangeListener listener) {
    myDataPackChangeListeners.remove(listener);
  }

  @NotNull
  public DataPack getDataPack() {
    return myRefresher.getCurrentDataPack();
  }

  @Override
  @Nullable
  public CommitId getCommitId(int commitIndex) {
    return myStorage.getCommitId(commitIndex);
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    return myStorage.getCommitIndex(hash, root);
  }

  @NotNull
  public VcsLogStorage getStorage() {
    return myStorage;
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

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Collection<VirtualFile> getRoots() {
    return myLogProviders.keySet();
  }

  @NotNull
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogProviders;
  }

  @NotNull
  public ContainingBranchesGetter getContainingBranchesGetter() {
    return myContainingBranchesGetter;
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i. e. it can query VCS just for the part of the log.
   */
  public void refresh(@NotNull Collection<VirtualFile> roots) {
    initialize();
    myRefresher.refresh(roots);
  }

  @NotNull
  public CommitDetailsGetter getCommitDetailsGetter() {
    return myDetailsGetter;
  }

  @NotNull
  public MiniDetailsGetter getMiniDetailsGetter() {
    return myMiniDetailsGetter;
  }

  @Override
  public void dispose() {
    SingleTaskController.SingleTask initialization;

    synchronized (myLock) {
      initialization = myInitialization;
      myInitialization = null;
      myState = State.DISPOSED;
    }

    if (initialization != null) {
      initialization.cancel();
      try {
        initialization.waitFor(1, TimeUnit.MINUTES);
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        LOG.warn(e);
      }
    }
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

  @NotNull
  public VcsLogUserResolver getUserNameResolver() {
    return myUserResolver;
  }

  @NotNull
  public VcsLogProgress getProgress() {
    return myRefresher.getProgress();
  }

  @NotNull
  public TopCommitsCache getTopCommitsCache() {
    return myTopCommitsDetailsCache;
  }

  @NotNull
  public VcsLogIndex getIndex() {
    return getModifiableIndex();
  }

  @NotNull
  VcsLogModifiableIndex getModifiableIndex() {
    return myIndex;
  }

  private enum State {
    CREATED, INITIALIZED, DISPOSED
  }

  private class MyVcsLogUserResolver extends VcsLogUserResolverBase implements Disposable {
    private final @NotNull DataPackChangeListener myListener = newDataPack -> {
      clearCache();
    };

    MyVcsLogUserResolver() {
      addDataPackChangeListener(myListener);
      Disposer.register(VcsLogData.this, this);
    }

    @NotNull
    @Override
    public Map<VirtualFile, VcsUser> getCurrentUsers() {
      return VcsLogData.this.getCurrentUser();
    }

    @NotNull
    @Override
    public Set<VcsUser> getAllUsers() {
      return VcsLogData.this.getAllUsers();
    }

    @Override
    public void dispose() {
      removeDataPackChangeListener(myListener);
    }
  }
}
