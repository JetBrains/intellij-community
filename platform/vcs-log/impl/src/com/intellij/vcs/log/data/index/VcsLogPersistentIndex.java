// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.concurrency.ConcurrentCollectionFactory;
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
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.SingleTaskController;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.data.VcsLogStorage;
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
import org.jetbrains.sqlite.AlreadyClosedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.intellij.openapi.vcs.VcsScopeKt.VcsScope;
import static com.intellij.vcs.log.util.PersistentUtil.calcLogId;

public final class VcsLogPersistentIndex implements VcsLogModifiableIndex, Disposable {
  static final Logger LOG = Logger.getInstance(VcsLogPersistentIndex.class);
  static final int VERSION = 18;
  public static final VcsLogProgress.ProgressKey INDEXING = new VcsLogProgress.ProgressKey("index");

  private final @NotNull Project myProject;
  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private final @NotNull VcsLogProgress myProgress;
  private final @NotNull Map<VirtualFile, VcsLogIndexer> myIndexers;
  private final @NotNull VcsLogStorage myStorage;
  private final @NotNull Set<VirtualFile> myRoots;
  private final @NotNull VcsLogBigRepositoriesList myBigRepositoriesList;
  private final @NotNull VcsLogIndexCollector myIndexCollector;
  private final @NotNull CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  private final @NotNull VcsLogStorageBackend myBackend;
  private final @NotNull IndexDataGetter myDataGetter;

  private final @NotNull SingleTaskController<IndexingRequest, Void> mySingleTaskController;
  private final @NotNull MyHeavyAwareListener myHeavyAwareListener;
  private final @NotNull AtomicReference<Boolean> myPostponedIndex = new AtomicReference<>(null);

  private final @NotNull Map<VirtualFile, AtomicInteger> myNumberOfTasks = new HashMap<>();
  private final @NotNull Map<VirtualFile, AtomicLong> myIndexingTime = new HashMap<>();
  private final @NotNull Map<VirtualFile, AtomicInteger> myIndexingLimit = new HashMap<>();
  private final @NotNull Map<VirtualFile, ConcurrentIntObjectMap<Integer>> myIndexingErrors = new HashMap<>();

  private final @NotNull List<IndexingFinishedListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private @NotNull Map<VirtualFile, IntSet> myCommitsToIndex = new HashMap<>();

  private final @NotNull IdleVcsLogIndexer myIdleIndexer;

  private VcsLogPersistentIndex(@NotNull Project project,
                                @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                @NotNull Map<VirtualFile, VcsLogIndexer> indexers,
                                @NotNull LinkedHashSet<VirtualFile> roots,
                                @NotNull VcsLogStorage storage,
                                @NotNull VcsLogStorageBackend backend,
                                @NotNull VcsLogProgress progress,
                                @NotNull VcsLogErrorHandler errorHandler,
                                @NotNull Disposable disposableParent) {
    myStorage = storage;
    myProject = project;
    myProgress = progress;
    myErrorHandler = errorHandler;
    myBigRepositoriesList = VcsLogBigRepositoriesList.getInstance();
    myIndexCollector = VcsLogIndexCollector.getInstance(myProject);

    myIndexers = indexers;
    myRoots = roots;

    myBackend = backend;
    myDataGetter = new IndexDataGetter(myProject, ContainerUtil.filter(providers, root -> myRoots.contains(root)),
                                       myBackend, myStorage, myErrorHandler);

    for (VirtualFile root : myRoots) {
      myNumberOfTasks.put(root, new AtomicInteger());
      myIndexingTime.put(root, new AtomicLong());
      myIndexingLimit.put(root, new AtomicInteger(getIndexingLimit()));
      myIndexingErrors.put(root, ConcurrentCollectionFactory.createConcurrentIntObjectMap());
    }

    mySingleTaskController = new MySingleTaskController(this);
    myHeavyAwareListener = new MyHeavyAwareListener(100);
    myHeavyAwareListener.start();

    myIdleIndexer = new IdleVcsLogIndexer(project, this, this);
    myIdleIndexer.start();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myDisposableFlag);
  }

  private static int getIndexingLimit() {
    int limitValue = Registry.intValue("vcs.log.index.limit.minutes");
    if (limitValue < 0) return -1;
    return Math.max(1, limitValue);
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
    if (myCommitsToIndex.isEmpty()) return;
    // for fresh index, wait for complete log to load and index everything in one command
    if (myBackend.isFresh() && !full) return;

    Map<VirtualFile, IntSet> commitsToIndex = myCommitsToIndex;
    myCommitsToIndex = new HashMap<>();

    boolean isFull = full && myBackend.isFresh();
    if (isFull) LOG.debug("Index storage for project " + myProject.getName() + " is fresh, scheduling full reindex");
    for (VirtualFile root : commitsToIndex.keySet()) {
      IntSet commits = commitsToIndex.get(root);
      if (commits.isEmpty()) continue;

      if (myBigRepositoriesList.isBig(root)) {
        myCommitsToIndex.put(root, commits); // put commits back in order to be able to reindex
        LOG.info("Indexing repository " + root.getName() + " is skipped");
        continue;
      }

      requestConsumer.accept(new IndexingRequest(root, myBackend.getPathsEncoder(), commits, isFull));
    }

    if (isFull) {
      myIndexCollector.reportFreshIndex();
      myBackend.setFresh(false);
    }
  }

  private void storeDetail(@NotNull VcsLogIndexer.CompressedDetails detail, @NotNull VcsLogWriter mutator) {
    try {
      mutator.putCommit(myStorage.getCommitIndex(detail.getId(), detail.getRoot()), detail);
    }
    catch (IOException | UncheckedIOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
    }
  }

  @Override
  public void markCorrupted() {
    myBackend.markCorrupted();
  }

  @Override
  public boolean isIndexed(int commit) {
    try {
      return myBackend.containsCommit(commit);
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
    return myRoots.contains(root) && !(myBigRepositoriesList.isBig(root));
  }

  @Override
  public synchronized void markForIndexing(int index, @NotNull VirtualFile root) {
    if (isIndexed(index) || !myRoots.contains(root)) return;
    IntCollectionUtil.add(myCommitsToIndex, root, index);
  }

  private synchronized void markForIndexing(@NotNull IntSet commits, @NotNull VirtualFile root) {
    IntSet commitsToIndex = myCommitsToIndex.computeIfAbsent(root, __ -> new IntOpenHashSet());
    commitsToIndex.addAll(commits);
  }

  @Override
  public @NotNull IndexDataGetter getDataGetter() {
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
    return myBackend.getStorageId();
  }

  @Override
  public void dispose() {
    myPostponedIndex.set(null);
  }

  @Override
  public @NotNull Set<VirtualFile> getIndexingRoots() {
    return myRoots;
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

  public static @Nullable VcsLogPersistentIndex create(@NotNull Project project,
                                                       @NotNull VcsLogStorage storage,
                                                       @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                       @NotNull VcsLogProgress progress,
                                                       @NotNull VcsLogErrorHandler errorHandler,
                                                       @NotNull Disposable disposableParent) {
    Map<VirtualFile, VcsLogIndexer> indexers = getAvailableIndexers(providers);
    LinkedHashSet<VirtualFile> roots = new LinkedHashSet<>(indexers.keySet());

    VcsLogStorageBackend backend;
    String logId = calcLogId(project, providers);
    if (storage instanceof VcsLogStorageBackend) {
      backend = (VcsLogStorageBackend)storage;
    }
    else {
      backend = PhmVcsLogStorageBackend.create(project, storage, roots, logId, errorHandler, disposableParent);
    }
    if (backend == null) return null;

    return new VcsLogPersistentIndex(project, providers, indexers, roots, storage, backend, progress, errorHandler, disposableParent);
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
      super("index", parent, unused -> {});
    }

    @Override
    protected int disposeLongTimeout() {
      if (SqliteVcsLogStorageBackendKt.isSqliteBackend(myBackend)) return 5000;

      return super.disposeLongTimeout();
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
              indicator.checkCanceled();
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
          task.accept(indicator);
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
    private static final int LOGGED_ERRORS_COUNT = 5;
    private static final int STOPPING_ERROR_COUNT = 30;

    private final @NotNull VirtualFile myRoot;
    private final @NotNull IntSet myCommits;
    private final @NotNull VcsLogIndexer.PathsEncoder myPathsEncoder;
    private final boolean myFull;

    private final int myFlushedCommitsNumber = SqliteVcsLogStorageBackendKt.isSqliteBackend(myBackend) ? 5000 : 15000;

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

      mySpan = TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder("git-log-indexing").startSpan();
      myScope = mySpan.makeCurrent();
      myStartTime = getCurrentTimeMillis();

      LOG.info("Indexing " + (myFull ? "full repository" : myCommits.size() + " commits") + " in " + myRoot.getName());

      VcsLogStorageBackend indexStorageBackend = Objects.requireNonNull(myBackend);
      final var mutator = indexStorageBackend.createWriter();
      boolean performCommit = false;
      try {
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
      catch (AlreadyClosedException e) {
        throw new ProcessCanceledException(e);
      }
      catch (ProcessCanceledException e) {
        performCommit = true;
        scheduleReindex();
        throw e;
      }
      catch (VcsException e) {
        if (indicator.isCanceled()) {
          performCommit = true;
          scheduleReindex();
          throw new ProcessCanceledException();
        }
        processException(e);
        scheduleReindex();
      }
      finally {
        try {
          mutator.close(performCommit);
        }
        catch (AlreadyClosedException | ProcessCanceledException ignored) {
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
      mySpan.setAttribute("numberOfCommits-" + myRoot.getName(), myNewIndexedCommits.get());
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
        LOG.debug("Reindexing of " + myRoot.getName() + " is not scheduled since dispose has already started. " +
                  unindexedCommits + " unindexed commits left.");
        return;
      }
      LOG.debug("Schedule reindexing of " + unindexedCommits + " commits in " + myRoot.getName());
      markCommits();
      scheduleIndex(false);
    }

    private void markCommits() {
      if (!myRoots.contains(myRoot)) return;

      try {
        IntSet missingCommits = myBackend.collectMissingCommits(myCommits);
        markForIndexing(missingCommits, myRoot);
      }
      catch (IOException e) {
        myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
      }
    }

    private void indexOneByOne(@NotNull IntStream commits, @NotNull ProgressIndicator indicator, @NotNull VcsLogWriter mutator)
      throws VcsException {
      // We pass hashes to VcsLogProvider#readFullDetails in batches
      // in order to avoid allocating too much memory for these hashes
      // a batch of 20k will occupy ~2.4Mb
      IntCollectionUtil.processBatches(commits, BATCH_SIZE, batch -> {
        indicator.checkCanceled();

        Map<@NotNull Integer, @NotNull CommitId> ids = myStorage.getCommitIds(batch);
        List<String> hashes = ContainerUtil.map(ids.values(), value -> value.getHash().asString());
        myIndexers.get(myRoot).readFullDetails(myRoot, hashes, myPathsEncoder, detail -> {
          indicator.checkCanceled();
          storeDetail(detail, mutator);
          if (myNewIndexedCommits.incrementAndGet() % myFlushedCommitsNumber == 0) {
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

        if (myNewIndexedCommits.incrementAndGet() % myFlushedCommitsNumber == 0) {
          mutator.flush();
        }

        checkShouldCancel(indicator);
      });
    }

    private void checkShouldCancel(@NotNull ProgressIndicator indicator) {
      long time = myIndexingTime.get(myRoot).get() + (getCurrentTimeMillis() - myStartTime);
      int limit = myIndexingLimit.get(myRoot).get();
      boolean isBigRoot = myBigRepositoriesList.isBig(myRoot);
      boolean isOvertime = !myIdleIndexer.isEnabled()
                           && (limit > 0 && time >= (Math.max(limit, 1L) * 60 * 1000) && !isBigRoot);
      if (isOvertime || (isBigRoot && !indicator.isCanceled())) {
        mySpan.setAttribute("cancelled", true);
        LOG.warn("Indexing " + myRoot.getName() + " was cancelled after " + StopWatch.formatTime(time));
        if (!isBigRoot) {
          myBigRepositoriesList.addRepository(myRoot);
        }
        if (isOvertime) {
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
