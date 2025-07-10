// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.index.*;
import com.intellij.vcs.log.impl.VcsLogCachesInvalidator;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.impl.VcsLogIndexer;
import com.intellij.vcs.log.impl.VcsLogStorageLocker;
import com.intellij.vcs.log.util.PersistentUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import io.opentelemetry.api.trace.Span;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static com.intellij.platform.vcs.impl.shared.telemetry.VcsScopeKt.VcsScope;

public final class VcsLogData implements Disposable, VcsLogDataProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogData.class);

  public static final VcsLogProgress.ProgressKey DATA_PACK_REFRESH = new VcsLogProgress.ProgressKey("data pack");

  private final @NotNull Project myProject;
  private final @NotNull Map<VirtualFile, VcsLogProvider> myLogProviders;
  private final @NotNull MiniDetailsGetter myMiniDetailsGetter;
  private final @NotNull CommitDetailsGetter myDetailsGetter;
  private final @NotNull CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  /**
   * Current username, as specified in the VCS settings.
   * It can be configured differently for different roots => store in a map.
   */
  private final Map<VirtualFile, VcsUser> myCurrentUser = new ConcurrentHashMap<>();

  /**
   * Cached details of the latest commits.
   * We store them separately from the cache of {@link DataGetter}, to make sure that they are always available,
   * which is important because these details will be constantly visible to the user,
   * thus it would be annoying to re-load them from VCS if the cache overflows.
   */
  private final @NotNull TopCommitsCache myTopCommitsDetailsCache;
  private final @NotNull VcsUserRegistryImpl myUserRegistry;
  private final @NotNull VcsLogUserResolver myUserResolver;
  private final @NotNull VcsLogStorageLocker myStorageLocker;
  private final @NotNull String myStorageId;
  private final @NotNull VcsLogStorage myStorage;
  private final @NotNull ContainingBranchesGetter myContainingBranchesGetter;
  private final @NotNull VcsLogRefresherImpl myRefresher;
  private final @NotNull List<DataPackChangeListener> myDataPackChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private final @NotNull VcsLogModifiableIndex myIndex;
  private final @NotNull IndexDiagnosticRunner myIndexDiagnosticRunner;

  private final @NotNull Object myLock = new Object();
  private @NotNull State myState = State.CREATED;
  private @Nullable SingleTaskController.SingleTask myInitialization = null;

  public VcsLogData(@NotNull Project project,
                    @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                    @NotNull VcsLogErrorHandler errorHandler,
                    boolean isIndexEnabled, @NotNull Disposable parentDisposable) {
    myProject = project;
    myLogProviders = logProviders;
    myUserRegistry = (VcsUserRegistryImpl)project.getService(VcsUserRegistry.class);
    myErrorHandler = errorHandler;

    VcsLogProgress progress = new VcsLogProgress(this);

    myStorageLocker = VcsLogStorageLocker.Companion.getInstance();
    myStorageId = PersistentUtil.calcLogId(myProject, myLogProviders);
    // storage can not be opened twice, so we have to wait for other clients (previously opened/closed project) to close it first
    myStorageLocker.acquireLock(myStorageId);
    try {
      Pair<VcsLogStorage, VcsLogModifiableIndex> storageAndIndex = createStorageAndIndex(progress, isIndexEnabled);
      myStorage = storageAndIndex.first;
      myIndex = storageAndIndex.second;
    }
    catch (Throwable e) {
      myStorageLocker.releaseLock(myStorageId);
      throw e;
    }

    myTopCommitsDetailsCache = new TopCommitsCache(myStorage);
    myMiniDetailsGetter = new MiniDetailsGetter(myProject, myStorage, logProviders, myTopCommitsDetailsCache, myIndex, this);
    myDetailsGetter = new CommitDetailsGetter(myStorage, logProviders, this);

    myRefresher = new VcsLogRefresherImpl(myProject, myStorage, myLogProviders, myUserRegistry, myIndex, progress, myTopCommitsDetailsCache,
                                          this::fireDataPackChangeEvent, getRecentCommitsCount());
    Disposer.register(this, myRefresher);

    myContainingBranchesGetter = new ContainingBranchesGetter(this, this);
    myUserResolver = new MyVcsLogUserResolver();

    myIndexDiagnosticRunner = new IndexDiagnosticRunner(myIndex, myStorage, myLogProviders.keySet(),
                                                        this::getDataPack, myDetailsGetter, myErrorHandler, this);

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

  private @NotNull Pair<VcsLogStorage, VcsLogModifiableIndex> createStorageAndIndex(@NotNull VcsLogProgress progress, boolean isIndexEnabled) {
    if (!VcsLogCachesInvalidator.getInstance().isValid()) {
      // this is not recoverable
      // restart won't help here
      // and cannot shut down ide because of this
      // so use memory storage (probably leading to out of memory at some point) + no index

      LOG.error("Could not delete caches at " + PersistentUtil.LOG_CACHE);
      myErrorHandler.displayMessage(VcsLogBundle.message("vcs.log.fatal.error.message", PersistentUtil.LOG_CACHE,
                                                         ApplicationNamesInfo.getInstance().getFullProductName()));
      return new Pair<>(new InMemoryStorage(), new EmptyIndex());
    }

    Map<VirtualFile, VcsLogIndexer> indexers = VcsLogPersistentIndex.getAvailableIndexers(myLogProviders);
    boolean isIndexSwitchedOnInRegistry = isIndexSwitchedOnInRegistry();
    boolean isIndexSwitchedOn = isIndexEnabled && isIndexSwitchedOnInRegistry;

    VcsLogStorage storage;
    VcsLogStorageBackend indexBackend;
    try {
      if (Registry.is("vcs.log.index.sqlite.storage", false)) {
        SqliteVcsLogStorageBackend sqliteBackend =
          new SqliteVcsLogStorageBackend(myProject, myStorageId, myLogProviders, myErrorHandler, this);
        storage = sqliteBackend;
        indexBackend = sqliteBackend;
      }
      else {
        Set<VirtualFile> indexingRoots = isIndexSwitchedOn ? new LinkedHashSet<>(indexers.keySet()) : Collections.emptySet();
        Pair<VcsLogStorage, VcsLogStorageBackend> storageAndIndexBackend = VcsLogStorageImpl.createStorageAndIndexBackend(myProject,
                                                                                                                          myStorageId,
                                                                                                                          myLogProviders,
                                                                                                                          indexingRoots,
                                                                                                                          myErrorHandler,
                                                                                                                          this);
        storage = storageAndIndexBackend.first;
        indexBackend = storageAndIndexBackend.second;
      }
    }
    catch (IOException e) {
      LOG.error("Falling back to in-memory hashes", e);
      return new Pair<>(new InMemoryStorage(), new EmptyIndex());
    }

    if (indexBackend == null || !isIndexSwitchedOn || indexers.isEmpty()) {
      if (!isIndexSwitchedOnInRegistry) LOG.info("Vcs log index is turned off in the registry");
      if (!isIndexEnabled) LOG.info("Vcs log index is turned off for " + VcsLogUtil.getProvidersMapText(myLogProviders));
      if (indexers.isEmpty()) LOG.info("No indexers found for project " + myProject.getName());
      return new Pair<>(storage, new EmptyIndex());
    }

    VcsLogPersistentIndex index = new VcsLogPersistentIndex(myProject, myLogProviders, indexers, storage, indexBackend, progress,
                                                            myErrorHandler, this);
    return new Pair<>(storage, index);
  }

  public void initialize() {
    myRefresher.initialize();
    readCurrentUser();
  }

  private void readCurrentUser() {
    synchronized (myLock) {
      if (myState.equals(State.CREATED)) {
        myState = State.INITIALIZED;
        Task.Backgroundable backgroundable = new Task.Backgroundable(myProject,
                                                                     VcsLogBundle.message("vcs.log.initial.reading.current.user.process"),
                                                                     false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.setIndeterminate(true);
            resetState();
            doReadCurrentUser();
          }

          @Override
          public void onCancel() {
            synchronized (myLock) {
              // Here be dragons:
              // VcsLogProgressManager can cancel us when it's getting disposed,
              // and we can also get canceled by invalid git executable.
              // Since we do not know what's up, we just restore the state,
              // and it is entirely possible to start another initialization after that.
              // Eventually, everything gets canceled for good in VcsLogData.dispose.
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

  private void doReadCurrentUser() {
    Span span = TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(LogData.ReadingCurrentUser.getName()).startSpan();
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
    span.end();
  }

  private void fireDataPackChangeEvent(final @NotNull DataPack dataPack) {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (DataPackChangeListener listener : myDataPackChangeListeners) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Starting data pack change listener " + listener);
        }
        listener.onDataPackChange(dataPack);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Finished data pack change listener " + listener);
        }
      }
    }, o -> myDisposableFlag.isDisposed());
  }

  public void addDataPackChangeListener(final @NotNull DataPackChangeListener listener) {
    myDataPackChangeListeners.add(listener);
  }

  public void removeDataPackChangeListener(@NotNull DataPackChangeListener listener) {
    myDataPackChangeListeners.remove(listener);
  }

  public @NotNull DataPack getDataPack() {
    return myRefresher.getCurrentDataPack();
  }

  @Override
  public @Nullable CommitId getCommitId(int commitIndex) {
    VcsCommitMetadata cachedData = myMiniDetailsGetter.getCachedData(commitIndex);
    if (cachedData != null) {
      return new CommitId(cachedData.getId(), cachedData.getRoot());
    }
    return myStorage.getCommitId(commitIndex);
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    return myStorage.getCommitIndex(hash, root);
  }

  public @NotNull VcsLogStorage getStorage() {
    return myStorage;
  }

  private void resetState() {
    myTopCommitsDetailsCache.clear();
  }

  public @NotNull Set<VcsUser> getAllUsers() {
    return myUserRegistry.getUsers();
  }

  public @NotNull Map<VirtualFile, VcsUser> getCurrentUser() {
    return myCurrentUser;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull Collection<VirtualFile> getRoots() {
    return myLogProviders.keySet();
  }

  public @NotNull Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogProviders;
  }

  public @NotNull ContainingBranchesGetter getContainingBranchesGetter() {
    return myContainingBranchesGetter;
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i.e., it can query VCS just for the part of the log.
   *
   * @param optimized - if request should be optimized see {@link VcsLogRefresher#refresh}
   */
  public void refresh(@NotNull Collection<VirtualFile> roots, boolean optimized) {
    initialize();
    myRefresher.refresh(roots, optimized);
  }

  public void refresh(@NotNull Collection<VirtualFile> roots) {
    refresh(roots, false);
  }

  public @NotNull CommitDetailsGetter getCommitDetailsGetter() {
    return myDetailsGetter;
  }

  @Override
  public @NotNull VcsLogCommitDataCache<VcsFullCommitDetails> getFullCommitDetailsCache() {
    return getCommitDetailsGetter();
  }

  public @NotNull MiniDetailsGetter getMiniDetailsGetter() {
    return myMiniDetailsGetter;
  }

  @Override
  public @NotNull VcsLogCommitDataCache<VcsCommitMetadata> getCommitMetadataCache() {
    return getMiniDetailsGetter();
  }

  @Override
  public void dispose() {
    try {
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

      if (myStorage instanceof VcsLogStorageImpl concreteStorage && !concreteStorage.isDisposed()) {
        LOG.error("Storage for $name was not disposed");
        Disposer.dispose(concreteStorage);
      }
    }
    finally {
      myStorageLocker.releaseLock(myStorageId);
    }
  }

  public @NotNull VcsLogProvider getLogProvider(@NotNull VirtualFile root) {
    return myLogProviders.get(root);
  }

  @ApiStatus.Internal
  public @NotNull VcsUserRegistryImpl getUserRegistry() {
    return myUserRegistry;
  }

  public @NotNull VcsLogUserResolver getUserNameResolver() {
    return myUserResolver;
  }

  public @NotNull VcsLogProgress getProgress() {
    return myRefresher.getProgress();
  }

  public @NotNull TopCommitsCache getTopCommitsCache() {
    return myTopCommitsDetailsCache;
  }

  public @NotNull VcsLogIndex getIndex() {
    //noinspection TestOnlyProblems
    return getModifiableIndex();
  }

  @TestOnly
  @NotNull
  @ApiStatus.Internal
  public VcsLogModifiableIndex getModifiableIndex() {
    return myIndex;
  }

  private enum State {
    CREATED, INITIALIZED, DISPOSED
  }

  private final class MyVcsLogUserResolver extends VcsLogUserResolverBase implements Disposable {
    private final @NotNull DataPackChangeListener myListener = newDataPack -> {
      clearCache();
    };

    MyVcsLogUserResolver() {
      addDataPackChangeListener(myListener);
      Disposer.register(VcsLogData.this, this);
    }

    @Override
    public @NotNull Map<VirtualFile, VcsUser> getCurrentUsers() {
      return VcsLogData.this.getCurrentUser();
    }

    @Override
    public @NotNull Set<VcsUser> getAllUsers() {
      return VcsLogData.this.getAllUsers();
    }

    @Override
    public void dispose() {
      removeDataPackChangeListener(myListener);
    }
  }

  public static boolean isIndexSwitchedOnInRegistry() {
    return getIndexingRegistryValue().asBoolean();
  }

  public static @NotNull RegistryValue getIndexingRegistryValue() {
    return Registry.get("vcs.log.index.enable");
  }

  public static int getRecentCommitsCount() {
    return Registry.intValue("vcs.log.recent.commits.count");
  }
}
