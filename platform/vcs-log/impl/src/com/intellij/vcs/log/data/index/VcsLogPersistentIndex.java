// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.StorageLockContext;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.SingleTaskController;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.data.VcsLogStorageImpl;
import com.intellij.vcs.log.impl.HeavyAwareListener;
import com.intellij.vcs.log.impl.VcsIndexableLogProvider;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.impl.VcsLogIndexer;
import com.intellij.vcs.log.statistics.VcsLogIndexCollector;
import com.intellij.vcs.log.util.IntCollectionUtil;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcs.log.util.StorageId;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.intellij.vcs.log.data.index.VcsLogFullDetailsIndex.INDEX;
import static com.intellij.vcs.log.util.PersistentUtil.calcIndexId;

public final class VcsLogPersistentIndex implements VcsLogModifiableIndex, Disposable {
  static final Logger LOG = Logger.getInstance(VcsLogPersistentIndex.class);
  static final int VERSION = 18;
  public static final VcsLogProgress.ProgressKey INDEXING = new VcsLogProgress.ProgressKey("index");

  private static final boolean useSqlite = Registry.is("vcs.log.index.sqlite.storage", false);

  private final @NotNull Project myProject;
  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private final @NotNull VcsLogProgress myProgress;
  private final @NotNull Map<VirtualFile, VcsLogIndexer> myIndexers;
  private final @NotNull VcsLogStorage myStorage;
  private final @NotNull Set<VirtualFile> myRoots;
  private final @NotNull VcsLogBigRepositoriesList myBigRepositoriesList;
  private final @NotNull VcsLogIndexCollector myIndexCollector;
  private final @NotNull CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  private final @NotNull StorageId myIndexStorageId;

  private final @Nullable IndexStorage myIndexStorage;
  private final @Nullable IndexDataGetter myDataGetter;

  private final @NotNull SingleTaskController<IndexingRequest, Void> mySingleTaskController;
  private final @NotNull MyHeavyAwareListener myHeavyAwareListener;
  private final @NotNull AtomicReference<Boolean> myPostponedIndex = new AtomicReference<>(null);

  private final @NotNull Map<VirtualFile, AtomicInteger> myNumberOfTasks = new HashMap<>();
  private final @NotNull Map<VirtualFile, AtomicLong> myIndexingTime = new HashMap<>();
  private final @NotNull Map<VirtualFile, AtomicInteger> myIndexingLimit = new HashMap<>();
  private final @NotNull Map<VirtualFile, ConcurrentIntObjectMap<Integer>> myIndexingErrors = new HashMap<>();

  private final @NotNull List<IndexingFinishedListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private @NotNull Map<VirtualFile, IntSet> myCommitsToIndex = new HashMap<>();

  public VcsLogPersistentIndex(@NotNull Project project,
                               @NotNull VcsLogStorage storage,
                               @NotNull VcsLogProgress progress,
                               @NotNull Map<VirtualFile, VcsLogProvider> providers,
                               @NotNull VcsLogErrorHandler errorHandler,
                               @NotNull Disposable disposableParent) {
    myStorage = storage;
    myProject = project;
    myProgress = progress;
    myErrorHandler = errorHandler;
    myBigRepositoriesList = VcsLogBigRepositoriesList.getInstance();
    myIndexCollector = VcsLogIndexCollector.getInstance(myProject);

    myIndexers = getAvailableIndexers(providers);
    myRoots = new LinkedHashSet<>(myIndexers.keySet());

    VcsUserRegistry userRegistry = myProject.getService(VcsUserRegistry.class);

    myIndexStorageId = new StorageId(myProject.getName(), INDEX, calcIndexId(myProject, myIndexers),
                                     VcsLogStorageImpl.VERSION + VERSION);
    myIndexStorage = createIndexStorage(myIndexStorageId, errorHandler, userRegistry);
    if (myIndexStorage != null) {
      myDataGetter = new IndexDataGetter(myProject, ContainerUtil.filter(providers, root -> myRoots.contains(root)),
                                         myIndexStorage, myStorage, myErrorHandler);
    }
    else {
      myDataGetter = null;
    }

    for (VirtualFile root : myRoots) {
      myNumberOfTasks.put(root, new AtomicInteger());
      myIndexingTime.put(root, new AtomicLong());
      myIndexingLimit.put(root, new AtomicInteger(getIndexingLimit()));
      myIndexingErrors.put(root, ConcurrentCollectionFactory.createConcurrentIntObjectMap());
    }

    mySingleTaskController = new MySingleTaskController(myIndexStorage != null ? myIndexStorage : this);
    myHeavyAwareListener = new MyHeavyAwareListener(100);
    myHeavyAwareListener.start();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myDisposableFlag);
  }

  private static int getIndexingLimit() {
    return Math.max(1, Registry.intValue("vcs.log.index.limit.minutes"));
  }

  private IndexStorage createIndexStorage(@NotNull StorageId indexStorageId, @NotNull VcsLogErrorHandler errorHandler,
                                          @NotNull VcsUserRegistry registry) {
    try {
      return IOUtil.openCleanOrResetBroken(() -> new IndexStorage(myProject, indexStorageId, myStorage, registry, myRoots, errorHandler, this),
                                           () -> {
                                             if (!indexStorageId.cleanupAllStorageFiles()) {
                                               LOG.error("Could not clean up storage files in " + indexStorageId.getProjectStorageDir());
                                             }
                                           });
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
    }
    return null;
  }

  @Override
  public void scheduleIndex(boolean full) {
    if (myHeavyAwareListener.isHeavy()) {
      LOG.debug("Indexing is postponed due to heavy activity");
      myPostponedIndex.updateAndGet(oldFull -> {
        if (oldFull == null) return full;
        return oldFull || full;
      });
    } else {
      doScheduleIndex(full);
    }
  }

  private void doScheduleIndex(boolean full) {
    doScheduleIndex(full, request -> mySingleTaskController.request(request));
  }

  @TestOnly
  void indexNow(boolean full) {
    doScheduleIndex(full, request -> request.run(myProgress.createProgressIndicator(INDEXING)));
  }

  private synchronized void doScheduleIndex(boolean full, @NotNull Consumer<? super IndexingRequest> requestConsumer) {
    if (myDisposableFlag.isDisposed()) return;
    if (myCommitsToIndex.isEmpty() || myIndexStorage == null) return;
    // for fresh index, wait for complete log to load and index everything in one command
    if (myIndexStorage.isFresh() && !full) return;

    Map<VirtualFile, IntSet> commitsToIndex = myCommitsToIndex;
    myCommitsToIndex = new HashMap<>();

    boolean isFull = full && myIndexStorage.isFresh();
    if (isFull) LOG.debug("Index storage for project " + myProject.getName() + " is fresh, scheduling full reindex");
    for (VirtualFile root : commitsToIndex.keySet()) {
      IntSet commits = commitsToIndex.get(root);
      if (commits.isEmpty()) continue;

      if (myBigRepositoriesList.isBig(root)) {
        myCommitsToIndex.put(root, commits); // put commits back in order to be able to reindex
        LOG.info("Indexing repository " + root.getName() + " is skipped");
        continue;
      }

      requestConsumer.consume(new IndexingRequest(root, myIndexStorage.paths.getPathsEncoder(), commits, isFull));
    }

    if (isFull) {
      myIndexCollector.reportFreshIndex();
      myIndexStorage.unmarkFresh();
    }
  }

  private void storeDetail(@NotNull VcsLogIndexer.CompressedDetails detail, @NotNull VcsLogWriter mutator) {
    if (myIndexStorage == null) {
      return;
    }

    try {
      int commitId = myStorage.getCommitIndex(detail.getId(), detail.getRoot());
      myIndexStorage.add(commitId, detail);
      mutator.putParents(commitId, detail.getParents(), hash -> myStorage.getCommitIndex(hash, detail.getRoot()));
      mutator.putCommit(commitId, detail, user -> myIndexStorage.users.getUserId(user));
    }
    catch (IOException | UncheckedIOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
    }
  }

  @Override
  public void markCorrupted() {
    if (myIndexStorage != null) {
      myIndexStorage.markCorrupted();
    }
  }

  @Override
  public boolean isIndexed(int commit) {
    try {
      return myIndexStorage == null || myIndexStorage.store.containsCommit(commit);
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
    }
    return false;
  }

  @Override
  public synchronized boolean isIndexed(@NotNull VirtualFile root) {
    return isIndexingEnabled(root) &&
           (!myCommitsToIndex.containsKey(root) && myNumberOfTasks.get(root).get() == 0);
  }

  @Override
  public boolean isIndexingEnabled(@NotNull VirtualFile root) {
    if (myIndexStorage == null) return false;
    return myRoots.contains(root) && !(myBigRepositoriesList.isBig(root));
  }

  @Override
  public synchronized void markForIndexing(int index, @NotNull VirtualFile root) {
    if (isIndexed(index) || !myRoots.contains(root)) return;
    IntCollectionUtil.add(myCommitsToIndex, root, index);
  }

  @Override
  public @Nullable IndexDataGetter getDataGetter() {
    if (myIndexStorage == null) return null;
    return myDataGetter;
  }

  @Override
  public void addListener(@NotNull IndexingFinishedListener l) {
    myListeners.add(l);
  }

  @Override
  public void removeListener(@NotNull IndexingFinishedListener l) {
    myListeners.remove(l);
  }

  public @NotNull StorageId getIndexStorageId() {
    return myIndexStorageId;
  }

  @Override
  public void dispose() {
    myPostponedIndex.set(null);
  }

  private static @NotNull Map<VirtualFile, VcsLogIndexer> getAvailableIndexers(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    Map<VirtualFile, VcsLogIndexer> indexers = new LinkedHashMap<>();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      VirtualFile root = entry.getKey();
      VcsLogProvider provider = entry.getValue();
      if (VcsLogProperties.SUPPORTS_INDEXING.getOrDefault(provider) && provider instanceof VcsIndexableLogProvider) {
        indexers.put(root, ((VcsIndexableLogProvider)provider).getIndexer());
      }
    }
    return indexers;
  }

  public static @NotNull Set<VirtualFile> getRootsForIndexing(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    return getAvailableIndexers(providers).keySet();
  }

  static final class IndexStorage implements Disposable {
    public final @NotNull VcsLogStorageBackend store;
    public final @NotNull VcsLogUserBiMap users;
    public final @NotNull VcsLogPathsIndex paths;

    IndexStorage(@NotNull Project project,
                 @NotNull StorageId indexStorageId,
                 @NotNull VcsLogStorage storage,
                 @NotNull VcsUserRegistry userRegistry,
                 @NotNull Set<VirtualFile> roots,
                 @NotNull VcsLogErrorHandler errorHandler,
                 @NotNull Disposable parentDisposable)
      throws IOException {
      Disposer.register(parentDisposable, this);

      try {
        StorageLockContext storageLockContext = new StorageLockContext();
        if (useSqlite) {
          store = new SqliteVcsLogStorageBackend(project);
        }
        else {
          store = new PhmVcsLogStorageBackend(indexStorageId, storageLockContext, errorHandler, this);
        }

        users = new VcsLogUserIndex(indexStorageId, storageLockContext, userRegistry, errorHandler, this);
        paths = new VcsLogPathsIndex(indexStorageId, storage, roots, storageLockContext, errorHandler, store, this);

        reportEmpty();
      }
      catch (Throwable t) {
        Disposer.dispose(this);
        throw t;
      }
    }

    void add(int commitId, @NotNull VcsLogIndexer.CompressedDetails detail) {
      users.update(commitId, detail);
      paths.update(commitId, detail);
    }

    private void reportEmpty() throws IOException {
      if (store.isEmpty()) {
        return;
      }

      var trigramsEmpty = store.getTrigramsEmpty();
      boolean usersEmpty = users.isEmpty();
      boolean pathsEmpty = paths.isEmpty();
      if ((trigramsEmpty != null && trigramsEmpty) || usersEmpty || pathsEmpty) {
        LOG.warn("Some of the index maps empty:\n" +
                 "trigrams empty " + trigramsEmpty + "\n" +
                 "users empty " + usersEmpty + "\n" +
                 "paths empty " + pathsEmpty);
      }
    }

    void markCorrupted() {
      try {
        store.markCorrupted();
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }

    public void unmarkFresh() {
      store.setFresh(false);
    }

    public boolean isFresh() {
      return store.isFresh();
    }

    @Override
    public void dispose() {
    }
  }

  private final class MyHeavyAwareListener extends HeavyAwareListener {

    private MyHeavyAwareListener(int delay) {
      super(myProject, delay, VcsLogPersistentIndex.this);
    }

    @Override
    public void heavyActivityEnded() {
      Boolean indexRequest = myPostponedIndex.getAndSet(null);
      if (indexRequest == null) return;
      doScheduleIndex(indexRequest);
    }

    @Override
    public void heavyActivityStarted() {
      mySingleTaskController.cancelCurrentTask();
    }
  }

  private class MySingleTaskController extends SingleTaskController<IndexingRequest, Void> {
    private static final int LOW_PRIORITY = Thread.MIN_PRIORITY;

    MySingleTaskController(@NotNull Disposable parent) {
      super("index", EmptyConsumer.getInstance(), parent);
    }

    @Override
    protected @NotNull SingleTask startNewBackgroundTask() {
      ProgressIndicator indicator = myProgress.createProgressIndicator(true, INDEXING);
      Consumer<ProgressIndicator> task = progressIndicator -> {
        int previousPriority = setMinimumPriority();
        try {
          IndexingRequest request;
          while ((request = popRequest()) != null) {
            try {
              request.run(progressIndicator);
              progressIndicator.checkCanceled();
            }
            catch (ProcessCanceledException reThrown) {
              throw reThrown;
            }
            catch (Throwable t) {
              request.processException(t);
            }
          }
        }
        finally {
          taskCompleted(null);
          resetPriority(previousPriority);
        }
      };
      Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
        ProgressManager.getInstance().runProcess(() -> {
          task.consume(indicator);
        }, indicator);
      });
      return new SingleTaskImpl(future, indicator);
    }

    public void resetPriority(int previousPriority) {
      if (Thread.currentThread().getPriority() == LOW_PRIORITY) Thread.currentThread().setPriority(previousPriority);
    }

    public int setMinimumPriority() {
      int previousPriority = Thread.currentThread().getPriority();
      try {
        Thread.currentThread().setPriority(LOW_PRIORITY);
      }
      catch (SecurityException e) {
        LOG.debug("Could not set indexing thread priority", e);
      }
      return previousPriority;
    }
  }

  private final class IndexingRequest {
    private static final int BATCH_SIZE = 20_000;
    private static final int FLUSHED_COMMITS_NUMBER = 15000;
    private static final int LOGGED_ERRORS_COUNT = 5;
    private static final int STOPPING_ERROR_COUNT = 30;
    private final @NotNull VirtualFile myRoot;
    private final @NotNull IntSet myCommits;
    private final @NotNull VcsLogIndexer.PathsEncoder myPathsEncoder;
    private final boolean myFull;

    private final @NotNull AtomicInteger myNewIndexedCommits = new AtomicInteger();
    private final @NotNull AtomicInteger myOldCommits = new AtomicInteger();
    private volatile long myStartTime;
    private Span mySpan;
    private Scope myScope;

    IndexingRequest(@NotNull VirtualFile root,
                    @NotNull VcsLogIndexer.PathsEncoder encoder,
                    @NotNull IntSet commits,
                    boolean full) {
      myRoot = root;
      myPathsEncoder = encoder;
      myCommits = commits;
      myFull = full;

      myNumberOfTasks.get(root).incrementAndGet();
    }

    public void run(@NotNull ProgressIndicator indicator) {
      if (myBigRepositoriesList.isBig(myRoot)) {
        LOG.info("Indexing repository " + myRoot.getName() + " is skipped");
        markCommits();
        myNumberOfTasks.get(myRoot).decrementAndGet();
        return;
      }

      indicator.setIndeterminate(false);
      indicator.setFraction(0);

      mySpan = TraceManager.INSTANCE.getTracer("vcs").spanBuilder("indexing").startSpan();
      myScope = mySpan.makeCurrent();
      myStartTime = getCurrentTimeMillis();

      LOG.info("Indexing " + (myFull ? "full repository" : myCommits.size() + " commits") + " in " + myRoot.getName());

      IndexStorage indexStorage = Objects.requireNonNull(myIndexStorage);
      final var mutator = indexStorage.store.createWriter();
      boolean performCommit = false;
      try {
        indexStorage.paths.setMutator(mutator);
        if (myFull) {
          indexAll(indicator, mutator);
        }
        else {
          IntStream commits = myCommits.intStream().filter(c -> {
            if (isIndexed(c)) {
              myOldCommits.incrementAndGet();
              return false;
            }
            return true;
          });

          indexOneByOne(commits, indicator, mutator);
        }
        performCommit = true;
      }
      catch (ProcessCanceledException e) {
        performCommit = true;
        scheduleReindex();
        throw e;
      }
      catch (VcsException e) {
        processException(e);
        scheduleReindex();
      }
      finally {
        indexStorage.paths.setMutator(null);
        try {
          myIndexStorage.users.flush();
          myIndexStorage.paths.flush();
          mutator.close(performCommit);
        }
        catch (ProcessCanceledException ignored) {
        }
        catch (Exception e) {
          myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
        }

        myNumberOfTasks.get(myRoot).decrementAndGet();

        myIndexingTime.get(myRoot).updateAndGet(t -> t + (getCurrentTimeMillis() - myStartTime));
        if (isIndexed(myRoot)) {
          long time = myIndexingTime.get(myRoot).getAndSet(0);
          myIndexCollector.reportIndexingTime(time);
          myListeners.forEach(listener -> listener.indexingFinished(myRoot));
        }

        report();
      }
    }

    private void flushUserAndPathMaps() {
      try {
        myIndexStorage.users.flush();
        myIndexStorage.paths.flush();
      }
      catch (Exception e) {
        myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
      }
    }

    private void processException(@NotNull Throwable e) {
      int errorHash = ThrowableInterner.computeTraceHashCode(e);
      int errors = myIndexingErrors.get(myRoot).cacheOrGet(errorHash, 0);
      myIndexingErrors.get(myRoot).put(errorHash, errors + 1);

      if (errors <= LOGGED_ERRORS_COUNT) {
        LOG.error("Error while indexing " + myRoot.getName(), e);
      }
      else if (errors >= STOPPING_ERROR_COUNT) {
        myBigRepositoriesList.addRepository(myRoot);
        LOG.error("Stopping indexing of " + myRoot.getName() + " due to the large amount of exceptions.", e);
      }
    }

    private static long getCurrentTimeMillis() {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void report() {
      String formattedTime = StopWatch.formatTime(getCurrentTimeMillis() - myStartTime);
      mySpan.setAttribute("numberOfCommits", myNewIndexedCommits.get());
      mySpan.setAttribute("rootName", myRoot.getName());
      if (myFull) {
        LOG.info(formattedTime +
                 " for indexing " +
                 myNewIndexedCommits + " commits in " + myRoot.getName());
      }
      else {
        int leftCommits = myCommits.size() - myNewIndexedCommits.get() - myOldCommits.get();
        String leftCommitsMessage = (leftCommits > 0) ? ". " + leftCommits + " commits left" : "";
        mySpan.setAttribute("totalCommits", myCommits.size());
        mySpan.setAttribute("commitsLeft", leftCommits);

        LOG.info(formattedTime +
                 " for indexing " +
                 myNewIndexedCommits +
                 " new commits out of " +
                 myCommits.size() + " in " + myRoot.getName() + leftCommitsMessage);
      }
      mySpan.end();
      myScope.close();
    }

    private void scheduleReindex() {
      int unindexedCommits = myCommits.size() - myNewIndexedCommits.get() - myOldCommits.get();
      if (mySingleTaskController.isClosed()) {
        LOG.debug("Reindexing of " + myRoot.getName() +  " is not scheduled since dispose has already started. " +
                  unindexedCommits + " unindexed commits left.");
        return;
      }
      LOG.debug("Schedule reindexing of " + unindexedCommits + " commits in " + myRoot.getName());
      markCommits();
      scheduleIndex(false);
    }

    private void markCommits() {
      synchronized (VcsLogPersistentIndex.this) {
        IndexStorage indexStorage = myIndexStorage;
        if (indexStorage == null || !myRoots.contains(myRoot)) {
          return;
        }
        try {
          IntSet set = myCommitsToIndex.computeIfAbsent(myRoot, __ -> new IntOpenHashSet());
          indexStorage.store.collectMissingCommits(myCommits, set);
        }
        catch (IOException e) {
          myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
        }
      }
    }

    private void indexOneByOne(@NotNull IntStream commits, @NotNull ProgressIndicator indicator, VcsLogWriter mutator) throws VcsException {
      // We pass hashes to VcsLogProvider#readFullDetails in batchesf
      // in order to avoid allocating too much memory for these hashes
      // a batch of 20k will occupy ~2.4Mb
      IntCollectionUtil.processBatches(commits, BATCH_SIZE, batch -> {
        indicator.checkCanceled();

        List<String> hashes = IntCollectionUtil.map2List(batch, value -> myStorage.getCommitId(value).getHash().asString());
        myIndexers.get(myRoot).readFullDetails(myRoot, hashes, myPathsEncoder, detail -> {
          indicator.checkCanceled();
          storeDetail(detail, mutator);
          if (myNewIndexedCommits.incrementAndGet() % FLUSHED_COMMITS_NUMBER == 0) {
            flushUserAndPathMaps();
            mutator.flush();
          }

          checkShouldCancel(indicator);
        });
      });
    }

    private void indexAll(@NotNull ProgressIndicator indicator, @NotNull VcsLogWriter mutator) throws VcsException {
      myIndexers.get(myRoot).readAllFullDetails(myRoot, myPathsEncoder, details -> {
        indicator.checkCanceled();
        storeDetail(details, mutator);

        if (myNewIndexedCommits.incrementAndGet() % FLUSHED_COMMITS_NUMBER == 0) {
          flushUserAndPathMaps();
          mutator.flush();
        }

        checkShouldCancel(indicator);
      });
    }

    private void checkShouldCancel(@NotNull ProgressIndicator indicator) {
      long time = myIndexingTime.get(myRoot).get() + (getCurrentTimeMillis() - myStartTime);
      int limit = myIndexingLimit.get(myRoot).get();
      boolean isOvertime = time >= (Math.max(limit, 1L) * 60 * 1000) && !myBigRepositoriesList.isBig(myRoot);
      if (isOvertime || (myBigRepositoriesList.isBig(myRoot) && !indicator.isCanceled())) {
        mySpan.setAttribute("cancelled", true);
        LOG.warn("Indexing " + myRoot.getName() + " was cancelled after " + StopWatch.formatTime(time));
        if (isOvertime) {
          myBigRepositoriesList.addRepository(myRoot);
          myIndexingLimit.get(myRoot).compareAndSet(limit,
                                                    Math.max(limit + getIndexingLimit(),
                                                             (int)((time / (getIndexingLimit() * 60000) + 1) * getIndexingLimit())));
        }
        indicator.cancel();
      }
    }

    @Override
    public String toString() {
      return "IndexingRequest of " + myCommits.size() + " commits in " + myRoot.getName() + (myFull ? " (full)" : "");
    }
  }
}
