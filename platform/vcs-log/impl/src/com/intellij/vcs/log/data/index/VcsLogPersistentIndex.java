// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsLogProperties;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.SingleTaskController;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.data.VcsLogStorageImpl;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.impl.HeavyAwareExecutor;
import com.intellij.vcs.log.impl.VcsIndexableLogProvider;
import com.intellij.vcs.log.impl.VcsLogIndexer;
import com.intellij.vcs.log.statistics.VcsLogIndexCollector;
import com.intellij.vcs.log.util.*;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.intellij.vcs.log.data.index.VcsLogFullDetailsIndex.INDEX;
import static com.intellij.vcs.log.util.PersistentUtil.calcIndexId;

public class VcsLogPersistentIndex implements VcsLogModifiableIndex, Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogPersistentIndex.class);
  private static final int VERSION = 18;
  public static final VcsLogProgress.ProgressKey INDEXING = new VcsLogProgress.ProgressKey("index");

  @NotNull private final Project myProject;
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;
  @NotNull private final VcsLogProgress myProgress;
  @NotNull private final Map<VirtualFile, VcsLogIndexer> myIndexers;
  @NotNull private final VcsLogStorage myStorage;
  @NotNull private final Set<VirtualFile> myRoots;
  @NotNull private final VcsLogBigRepositoriesList myBigRepositoriesList;
  @NotNull private final VcsLogIndexCollector myIndexCollector;
  @NotNull private final CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  @Nullable private final IndexStorage myIndexStorage;
  @Nullable private final IndexDataGetter myDataGetter;

  @NotNull private final SingleTaskController<IndexingRequest, Void> mySingleTaskController;
  @NotNull private final Map<VirtualFile, AtomicInteger> myNumberOfTasks = new HashMap<>();
  @NotNull private final Map<VirtualFile, AtomicLong> myIndexingTime = new HashMap<>();
  @NotNull private final Map<VirtualFile, AtomicInteger> myIndexingLimit = new HashMap<>();
  @NotNull private final Map<VirtualFile, ConcurrentIntObjectMap<Integer>> myIndexingErrors = new HashMap<>();

  @NotNull private final List<IndexingFinishedListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull private Map<VirtualFile, IntSet> myCommitsToIndex = new HashMap<>();

  public VcsLogPersistentIndex(@NotNull Project project,
                               @NotNull VcsLogStorage storage,
                               @NotNull VcsLogProgress progress,
                               @NotNull Map<VirtualFile, VcsLogProvider> providers,
                               @NotNull FatalErrorHandler fatalErrorsConsumer,
                               @NotNull Disposable disposableParent) {
    myStorage = storage;
    myProject = project;
    myProgress = progress;
    myFatalErrorsConsumer = fatalErrorsConsumer;
    myBigRepositoriesList = VcsLogBigRepositoriesList.getInstance();
    myIndexCollector = VcsLogIndexCollector.getInstance(myProject);

    myIndexers = getAvailableIndexers(providers);
    myRoots = new LinkedHashSet<>(myIndexers.keySet());

    VcsUserRegistry userRegistry = myProject.getService(VcsUserRegistry.class);

    myIndexStorage = createIndexStorage(fatalErrorsConsumer, myProject.getName(), calcIndexId(myProject, myIndexers), userRegistry);
    if (myIndexStorage != null) {
      myDataGetter = new IndexDataGetter(myProject, myRoots, myIndexStorage, myStorage, myFatalErrorsConsumer);
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

    mySingleTaskController = new MySingleTaskController(project, myIndexStorage != null ? myIndexStorage : this);

    Disposer.register(disposableParent, this);
    Disposer.register(this, myDisposableFlag);
  }

  private static int getIndexingLimit() {
    return Math.max(1, Registry.intValue("vcs.log.index.limit.minutes"));
  }

  protected IndexStorage createIndexStorage(@NotNull FatalErrorHandler fatalErrorHandler,
                                            @NotNull String projectName, @NotNull String logId, @NotNull VcsUserRegistry registry) {
    try {
      return IOUtil.openCleanOrResetBroken(() -> new IndexStorage(projectName, logId, myStorage, registry,
                                                                  myRoots, fatalErrorHandler, this),
                                           () -> IndexStorage.cleanup(projectName, logId));
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return null;
  }

  @Override
  public void scheduleIndex(boolean full) {
    doScheduleIndex(full, request -> mySingleTaskController.request(request));
  }

  @TestOnly
  void indexNow(boolean full) {
    doScheduleIndex(full, request -> request.run(myProgress.createProgressIndicator(INDEXING)));
  }

  private synchronized void doScheduleIndex(boolean full, @NotNull Consumer<IndexingRequest> requestConsumer) {
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

  private void storeDetail(@NotNull VcsLogIndexer.CompressedDetails detail) {
    if (myIndexStorage == null) return;
    try {
      int index = myStorage.getCommitIndex(detail.getId(), detail.getRoot());

      myIndexStorage.messages.put(index, detail.getFullMessage());
      myIndexStorage.trigrams.update(index, detail);
      myIndexStorage.users.update(index, detail);
      myIndexStorage.paths.update(index, detail);
      myIndexStorage.parents.put(index, ContainerUtil.map(detail.getParents(), p -> myStorage.getCommitIndex(p, detail.getRoot())));
      // we know the whole graph without timestamps now
      if (!detail.getAuthor().equals(detail.getCommitter())) {
        myIndexStorage.committers.put(index, myIndexStorage.users.getUserId(detail.getCommitter()));
      }
      myIndexStorage.timestamps.put(index, Pair.create(detail.getAuthorTime(), detail.getCommitTime()));

      myIndexStorage.commits.put(index);
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
  }

  private void flush() {
    try {
      if (myIndexStorage != null) {
        myIndexStorage.messages.force();
        myIndexStorage.trigrams.flush();
        myIndexStorage.users.flush();
        myIndexStorage.paths.flush();
        myIndexStorage.parents.force();
        myIndexStorage.commits.flush();
        myIndexStorage.committers.force();
        myIndexStorage.timestamps.force();
      }
    }
    catch (StorageException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
  }

  @Override
  public void markCorrupted() {
    if (myIndexStorage != null) myIndexStorage.commits.markCorrupted();
  }

  @Override
  public boolean isIndexed(int commit) {
    try {
      return myIndexStorage == null || myIndexStorage.commits.contains(commit);
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
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

  @Nullable
  @Override
  public IndexDataGetter getDataGetter() {
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

  @Override
  public void dispose() {
  }

  @NotNull
  private static Map<VirtualFile, VcsLogIndexer> getAvailableIndexers(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
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

  @NotNull
  public static Set<VirtualFile> getRootsForIndexing(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    return getAvailableIndexers(providers).keySet();
  }

  static class IndexStorage implements Disposable {
    private static final String COMMITS = "commits";
    private static final String MESSAGES = "messages";
    private static final String PARENTS = "parents";
    private static final String COMMITTERS = "committers";
    private static final String TIMESTAMPS = "timestamps";
    @NotNull public final PersistentSet<Integer> commits;
    @NotNull public final PersistentMap<Integer, String> messages;
    @NotNull public final PersistentMap<Integer, List<Integer>> parents;
    @NotNull public final PersistentMap<Integer, Integer> committers;
    @NotNull public final PersistentMap<Integer, Pair<Long, Long>> timestamps;
    @NotNull public final VcsLogMessagesTrigramIndex trigrams;
    @NotNull public final VcsLogUserIndex users;
    @NotNull public final VcsLogPathsIndex paths;

    private volatile boolean myIsFresh;

    IndexStorage(@NotNull String projectName,
                 @NotNull String logId,
                 @NotNull VcsLogStorage storage,
                 @NotNull VcsUserRegistry userRegistry,
                 @NotNull Set<VirtualFile> roots,
                 @NotNull FatalErrorHandler fatalErrorHandler,
                 @NotNull Disposable parentDisposable)
      throws IOException {
      Disposer.register(parentDisposable, this);

      try {
        StorageId storageId = indexStorageId(projectName, logId);
        StorageLockContext storageLockContext = new StorageLockContext(true);

        Path commitsStorage = storageId.getStorageFile(COMMITS);
        myIsFresh = !Files.exists(commitsStorage);
        commits = new PersistentSetImpl<>(commitsStorage, EnumeratorIntegerDescriptor.INSTANCE, Page.PAGE_SIZE, storageLockContext,
                                          storageId.getVersion());
        Disposer.register(this, () -> catchAndWarn(commits::close));

        messages = new PersistentHashMap<>(storageId.getStorageFile(MESSAGES), EnumeratorIntegerDescriptor.INSTANCE,
                                           EnumeratorStringDescriptor.INSTANCE, Page.PAGE_SIZE, storageId.getVersion(),
                                           storageLockContext);
        Disposer.register(this, () -> catchAndWarn(messages::close));

        trigrams = new VcsLogMessagesTrigramIndex(storageId, storageLockContext, fatalErrorHandler, this);
        users = new VcsLogUserIndex(storageId, storageLockContext, userRegistry, fatalErrorHandler, this);
        paths = new VcsLogPathsIndex(storageId, storage, roots, storageLockContext, fatalErrorHandler, this);

        Path parentsStorage = storageId.getStorageFile(PARENTS);
        parents = new PersistentHashMap<>(parentsStorage, EnumeratorIntegerDescriptor.INSTANCE,
                                          new IntListDataExternalizer(), Page.PAGE_SIZE, storageId.getVersion(), storageLockContext);
        Disposer.register(this, () -> catchAndWarn(parents::close));

        Path committersStorage = storageId.getStorageFile(COMMITTERS);
        committers = new PersistentHashMap<>(committersStorage, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE,
                                             Page.PAGE_SIZE, storageId.getVersion(), storageLockContext);
        Disposer.register(this, () -> catchAndWarn(committers::close));

        Path timestampsStorage = storageId.getStorageFile(TIMESTAMPS);
        timestamps = new PersistentHashMap<>(timestampsStorage, EnumeratorIntegerDescriptor.INSTANCE, new LongPairDataExternalizer(),
                                             Page.PAGE_SIZE, storageId.getVersion(), storageLockContext);
        Disposer.register(this, () -> catchAndWarn(timestamps::close));

        checkConsistency();
      }
      catch (Throwable t) {
        Disposer.dispose(this);
        throw t;
      }
    }

    private void checkConsistency() throws IOException {
      if (!commits.isEmpty()) {
        boolean trigramsEmpty = trigrams.isEmpty();
        boolean usersEmpty = users.isEmpty();
        boolean pathsEmpty = paths.isEmpty();
        if (trigramsEmpty || usersEmpty) {
          IOException exception = new IOException("Broken index maps:\n" +
                                                  "trigrams empty " + trigramsEmpty + "\n" +
                                                  "users empty " + usersEmpty + "\n" +
                                                  "paths empty " + pathsEmpty);
          LOG.error(exception);
          throw exception;
        }
        if (pathsEmpty) {
          LOG.warn("Paths map is empty");
        }
      }
    }

    void markCorrupted() {
      catchAndWarn(commits::markCorrupted);
    }

    public void unmarkFresh() {
      myIsFresh = false;
    }

    public boolean isFresh() {
      return myIsFresh;
    }

    @Override
    public void dispose() {
    }

    private static void catchAndWarn(@NotNull ThrowableRunnable<IOException> runnable) {
      try {
        runnable.run();
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }

    private static void cleanup(@NotNull String projectName, @NotNull String logId) {
      StorageId storageId = indexStorageId(projectName, logId);
      if (!storageId.cleanupAllStorageFiles()) {
        LOG.error("Could not clean up storage files in " + storageId.getProjectStorageDir());
      }
    }

    @NotNull
    private static StorageId indexStorageId(@NotNull String projectName, @NotNull String logId) {
      return new StorageId(projectName, INDEX, logId, VcsLogStorageImpl.VERSION + VERSION);
    }
  }

  private class MySingleTaskController extends SingleTaskController<IndexingRequest, Void> {
    private static final int LOW_PRIORITY = Thread.MIN_PRIORITY;
    @NotNull private final HeavyAwareExecutor myHeavyAwareExecutor;

    MySingleTaskController(@NotNull Project project, @NotNull Disposable parent) {
      super("index", EmptyConsumer.getInstance(), parent);
      myHeavyAwareExecutor = new HeavyAwareExecutor(project, 50, 100, VcsLogPersistentIndex.this);
    }

    @NotNull
    @Override
    protected SingleTask startNewBackgroundTask() {
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
      Future<?> future = myHeavyAwareExecutor.executeOutOfHeavyOrPowerSave(task, indicator);
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

  private class IndexingRequest {
    private static final int BATCH_SIZE = 20000;
    private static final int FLUSHED_COMMITS_NUMBER = 15000;
    private static final int LOGGED_ERRORS_COUNT = 10;
    private static final int STOPPING_ERROR_COUNT = 100;
    @NotNull private final VirtualFile myRoot;
    @NotNull private final IntSet myCommits;
    @NotNull private final VcsLogIndexer.PathsEncoder myPathsEncoder;
    private final boolean myFull;

    @NotNull private final AtomicInteger myNewIndexedCommits = new AtomicInteger();
    @NotNull private final AtomicInteger myOldCommits = new AtomicInteger();
    private volatile long myStartTime;

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

      myStartTime = getCurrentTimeMillis();

      LOG.info("Indexing " + (myFull ? "full repository" : myCommits.size() + " commits") + " in " + myRoot.getName());

      try {
        try {
          if (myFull) {
            indexAll(indicator);
          }
          else {
            IntStream commits = myCommits.intStream().filter(c -> {
              if (isIndexed(c)) {
                myOldCommits.incrementAndGet();
                return false;
              }
              return true;
            });

            indexOneByOne(commits, indicator);
          }
        }
        catch (ProcessCanceledException e) {
          scheduleReindex();
          throw e;
        }
        catch (VcsException e) {
          processException(e);
          scheduleReindex();
        }
      }
      finally {
        myNumberOfTasks.get(myRoot).decrementAndGet();

        myIndexingTime.get(myRoot).updateAndGet(t -> t + (getCurrentTimeMillis() - myStartTime));
        if (isIndexed(myRoot)) {
          long time = myIndexingTime.get(myRoot).getAndSet(0);
          myIndexCollector.reportIndexingTime(time);
          myListeners.forEach(listener -> listener.indexingFinished(myRoot));
        }

        report();

        flush();
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

    private long getCurrentTimeMillis() {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private void report() {
      String formattedTime = StopWatch.formatTime(getCurrentTimeMillis() - myStartTime);
      if (myFull) {
        LOG.info(formattedTime +
                 " for indexing " +
                 myNewIndexedCommits + " commits in " + myRoot.getName());
      }
      else {
        int leftCommits = myCommits.size() - myNewIndexedCommits.get() - myOldCommits.get();
        String leftCommitsMessage = (leftCommits > 0) ? ". " + leftCommits + " commits left" : "";

        LOG.info(formattedTime +
                 " for indexing " +
                 myNewIndexedCommits +
                 " new commits out of " +
                 myCommits.size() + " in " + myRoot.getName() + leftCommitsMessage);
      }
    }

    private void scheduleReindex() {
      LOG.debug("Schedule reindexing of " +
                (myCommits.size() - myNewIndexedCommits.get() - myOldCommits.get()) +
                " commits in " +
                myRoot.getName());
      markCommits();
      scheduleIndex(false);
    }

    private void markCommits() {
      myCommits.forEach(value -> {
        markForIndexing(value, myRoot);
      });
    }

    private void indexOneByOne(@NotNull IntStream commits, @NotNull ProgressIndicator indicator) throws VcsException {
      // We pass hashes to VcsLogProvider#readFullDetails in batches
      // in order to avoid allocating too much memory for these hashes
      // a batch of 20k will occupy ~2.4Mb
      IntCollectionUtil.processBatches(commits, BATCH_SIZE, batch -> {
        indicator.checkCanceled();

        List<String> hashes = IntCollectionUtil.map2List(batch, value -> myStorage.getCommitId(value).getHash().asString());
        myIndexers.get(myRoot).readFullDetails(myRoot, hashes, myPathsEncoder, detail -> {
          storeDetail(detail);
          if (myNewIndexedCommits.incrementAndGet() % FLUSHED_COMMITS_NUMBER == 0) flush();

          checkShouldCancel(indicator);
        });
      });
    }

    public void indexAll(@NotNull ProgressIndicator indicator) throws VcsException {
      myIndexers.get(myRoot).readAllFullDetails(myRoot, myPathsEncoder, details -> {
        storeDetail(details);

        if (myNewIndexedCommits.incrementAndGet() % FLUSHED_COMMITS_NUMBER == 0) flush();

        checkShouldCancel(indicator);
      });
    }

    private void checkShouldCancel(@NotNull ProgressIndicator indicator) {
      long time = myIndexingTime.get(myRoot).get() + (getCurrentTimeMillis() - myStartTime);
      int limit = myIndexingLimit.get(myRoot).get();
      boolean isOvertime = time >= (Math.max(limit, 1L) * 60 * 1000) && !myBigRepositoriesList.isBig(myRoot);
      if (isOvertime || (myBigRepositoriesList.isBig(myRoot) && !indicator.isCanceled())) {
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