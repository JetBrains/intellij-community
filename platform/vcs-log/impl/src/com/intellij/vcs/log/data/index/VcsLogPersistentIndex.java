/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.EmptyIntHashSet;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.impl.HeavyAwareExecutor;
import com.intellij.vcs.log.ui.filter.VcsLogTextFilterImpl;
import com.intellij.vcs.log.util.PersistentSet;
import com.intellij.vcs.log.util.PersistentSetImpl;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcs.log.util.TroveUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.intellij.vcs.log.data.index.VcsLogFullDetailsIndex.INDEX;
import static com.intellij.vcs.log.util.PersistentUtil.*;
import static com.intellij.vcs.log.util.TroveUtil.collect;

public class VcsLogPersistentIndex implements VcsLogIndex, Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogPersistentIndex.class);
  private static final int VERSION = 2;

  @NotNull private final Project myProject;
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;
  @NotNull private final VcsLogProgress myProgress;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myProviders;
  @NotNull private final VcsLogStorage myStorage;
  @NotNull private final VcsUserRegistryImpl myUserRegistry;
  @NotNull private final Set<VirtualFile> myRoots;

  @Nullable private final IndexStorage myIndexStorage;
  @Nullable private final IndexDataGetter myDataGetter;

  @NotNull private final SingleTaskController<IndexingRequest, Void> mySingleTaskController = new MySingleTaskController();
  @NotNull private final Map<VirtualFile, AtomicInteger> myNumberOfTasks = ContainerUtil.newHashMap();

  @NotNull private final List<IndexingFinishedListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull private Map<VirtualFile, TIntHashSet> myCommitsToIndex = ContainerUtil.newHashMap();

  public VcsLogPersistentIndex(@NotNull Project project,
                               @NotNull VcsLogStorage storage,
                               @NotNull VcsLogProgress progress,
                               @NotNull Map<VirtualFile, VcsLogProvider> providers,
                               @NotNull FatalErrorHandler fatalErrorsConsumer,
                               @NotNull Disposable disposableParent) {
    myStorage = storage;
    myProject = project;
    myProgress = progress;
    myProviders = providers;
    myFatalErrorsConsumer = fatalErrorsConsumer;
    myRoots = ContainerUtil.newLinkedHashSet();

    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      if (VcsLogProperties.get(entry.getValue(), VcsLogProperties.SUPPORTS_INDEXING)) {
        myRoots.add(entry.getKey());
      }
    }

    myUserRegistry = (VcsUserRegistryImpl)ServiceManager.getService(myProject, VcsUserRegistry.class);

    myIndexStorage = createIndexStorage(fatalErrorsConsumer, calcLogId(myProject, providers));
    if (myIndexStorage != null) {
      myDataGetter = new IndexDataGetter(myProject, myRoots, myIndexStorage, myFatalErrorsConsumer);
    }
    else {
      myDataGetter = null;
    }

    for (VirtualFile root : myRoots) {
      myNumberOfTasks.put(root, new AtomicInteger());
    }

    Disposer.register(disposableParent, this);
  }

  protected IndexStorage createIndexStorage(@NotNull FatalErrorHandler fatalErrorHandler, @NotNull String logId) {
    try {
      return IOUtil.openCleanOrResetBroken(() -> new IndexStorage(logId, myUserRegistry, myRoots, fatalErrorHandler, this),
                                           () -> IndexStorage.cleanup(logId));
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return null;
  }

  public static int getVersion() {
    return VcsLogStorageImpl.VERSION + VERSION;
  }

  @Override
  public synchronized void scheduleIndex(boolean full) {
    if (myCommitsToIndex.isEmpty() || myIndexStorage == null) return;
    Map<VirtualFile, TIntHashSet> commitsToIndex = myCommitsToIndex;

    for (VirtualFile root : commitsToIndex.keySet()) {
      myNumberOfTasks.get(root).incrementAndGet();
    }
    myCommitsToIndex = ContainerUtil.newHashMap();

    boolean isFull = full && myIndexStorage.isFresh();
    if (isFull) LOG.debug("Index storage for project " + myProject.getName() + " is fresh, scheduling full reindex");
    for (VirtualFile root : commitsToIndex.keySet()) {
      TIntHashSet commits = commitsToIndex.get(root);
      if (!commits.isEmpty()) {
        mySingleTaskController.request(new IndexingRequest(root, commits, isFull));
      }
    }
    if (isFull) myIndexStorage.unmarkFresh();
  }

  private void storeDetail(@NotNull VcsFullCommitDetails detail) {
    if (myIndexStorage == null) return;
    try {
      int index = myStorage.getCommitIndex(detail.getId(), detail.getRoot());

      myIndexStorage.messages.put(index, detail.getFullMessage());
      myIndexStorage.trigrams.update(index, detail);
      myIndexStorage.users.update(index, detail);
      myIndexStorage.paths.update(index, detail);
      myIndexStorage.parents.put(index, ContainerUtil.map(detail.getParents(), p -> myStorage.getCommitIndex(p, detail.getRoot())));
      // we know the whole graph without timestamps now

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
      }
    }
    catch (StorageException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
  }

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
    return myRoots.contains(root) && (!myCommitsToIndex.containsKey(root) && myNumberOfTasks.get(root).get() == 0);
  }

  @Override
  public synchronized void markForIndexing(int index, @NotNull VirtualFile root) {
    if (isIndexed(index) || !myRoots.contains(root)) return;
    TIntHashSet set = myCommitsToIndex.get(root);
    if (set == null) {
      set = new TIntHashSet();
      myCommitsToIndex.put(root, set);
    }
    set.add(index);
  }

  @NotNull
  private <T> TIntHashSet filter(@NotNull PersistentMap<Integer, T> map, @NotNull Condition<T> condition) {
    TIntHashSet result = new TIntHashSet();
    if (myIndexStorage == null) return result;
    try {
      Processor<Integer> processor = integer -> {
        try {
          T value = map.get(integer);
          if (value != null) {
            if (condition.value(value)) {
              result.add(integer);
            }
          }
        }
        catch (IOException e) {
          myFatalErrorsConsumer.consume(this, e);
          return false;
        }
        return true;
      };
      if (myIndexStorage.messages instanceof PersistentHashMap) {
        ((PersistentHashMap<Integer, T>)myIndexStorage.messages).processKeysWithExistingMapping(processor);
      }
      else {
        myIndexStorage.messages.processKeys(processor);
      }
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }

    return result;
  }

  @NotNull
  private TIntHashSet filterUsers(@NotNull Set<VcsUser> users) {
    if (myIndexStorage != null) {
      try {
        return myIndexStorage.users.getCommitsForUsers(users);
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
      catch (RuntimeException e) {
        processRuntimeException(e);
      }
    }
    return new TIntHashSet();
  }

  @NotNull
  private TIntHashSet filterPaths(@NotNull Collection<FilePath> paths) {
    if (myIndexStorage != null) {
      try {
        return myIndexStorage.paths.getCommitsForPaths(paths);
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
      catch (RuntimeException e) {
        processRuntimeException(e);
      }
    }
    return new TIntHashSet();
  }

  @NotNull
  public TIntHashSet filterMessages(@NotNull VcsLogTextFilter filter) {
    if (myIndexStorage != null) {
      try {
        if (!filter.isRegex()) {
          TIntHashSet commitsForSearch = myIndexStorage.trigrams.getCommitsForSubstring(filter.getText());
          if (commitsForSearch != null) {
            TIntHashSet result = new TIntHashSet();
            commitsForSearch.forEach(commit -> {
              try {
                String value = myIndexStorage.messages.get(commit);
                if (value != null) {
                  if (VcsLogTextFilterImpl.matches(filter, value)) {
                    result.add(commit);
                  }
                }
              }
              catch (IOException e) {
                myFatalErrorsConsumer.consume(this, e);
                return false;
              }
              return true;
            });
            return result;
          }
        }
      }
      catch (StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
      catch (RuntimeException e) {
        processRuntimeException(e);
      }

      return filter(myIndexStorage.messages, message -> VcsLogTextFilterImpl.matches(filter, message));
    }

    return EmptyIntHashSet.INSTANCE;
  }

  private void processRuntimeException(@NotNull RuntimeException e) {
    if (e instanceof ProcessCanceledException) throw e;
    if (myIndexStorage != null) myIndexStorage.markCorrupted();
    if (e.getCause() instanceof IOException || e.getCause() instanceof StorageException) {
      myFatalErrorsConsumer.consume(this, e);
    }
    else {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean canFilter(@NotNull List<VcsLogDetailsFilter> filters) {
    if (filters.isEmpty() || myIndexStorage == null) return false;
    for (VcsLogDetailsFilter filter : filters) {
      if (filter instanceof VcsLogTextFilter ||
          filter instanceof VcsLogUserFilter ||
          filter instanceof VcsLogStructureFilter) {
        continue;
      }
      return false;
    }
    return true;
  }

  @Override
  @NotNull
  public Set<Integer> filter(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    VcsLogTextFilter textFilter = ContainerUtil.findInstance(detailsFilters, VcsLogTextFilter.class);
    VcsLogUserFilter userFilter = ContainerUtil.findInstance(detailsFilters, VcsLogUserFilter.class);
    VcsLogStructureFilter pathFilter = ContainerUtil.findInstance(detailsFilters, VcsLogStructureFilter.class);

    TIntHashSet filteredByMessage = null;
    if (textFilter != null) {
      filteredByMessage = filterMessages(textFilter);
    }

    TIntHashSet filteredByUser = null;
    if (userFilter != null) {
      Set<VcsUser> users = ContainerUtil.newHashSet();
      for (VirtualFile root : myRoots) {
        users.addAll(userFilter.getUsers(root));
      }

      filteredByUser = filterUsers(users);
    }

    TIntHashSet filteredByPath = null;
    if (pathFilter != null) {
      filteredByPath = filterPaths(pathFilter.getFiles());
    }

    return TroveUtil.intersect(filteredByMessage, filteredByPath, filteredByUser);
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

  static class IndexStorage {
    private static final String COMMITS = "commits";
    private static final String MESSAGES = "messages";
    private static final String PARENTS = "parents";
    private static final int MESSAGES_VERSION = 0;
    @NotNull public final PersistentSet<Integer> commits;
    @NotNull public final PersistentMap<Integer, String> messages;
    @NotNull public final PersistentMap<Integer, List<Integer>> parents;
    @NotNull public final VcsLogMessagesTrigramIndex trigrams;
    @NotNull public final VcsLogUserIndex users;
    @NotNull public final VcsLogPathsIndex paths;

    private volatile boolean myIsFresh;

    public IndexStorage(@NotNull String logId,
                        @NotNull VcsUserRegistryImpl userRegistry,
                        @NotNull Set<VirtualFile> roots,
                        @NotNull FatalErrorHandler fatalErrorHandler,
                        @NotNull Disposable parentDisposable)
      throws IOException {
      Disposable disposable = Disposer.newDisposable();
      Disposer.register(parentDisposable, disposable);

      try {
        int version = getVersion();

        File commitsStorage = getStorageFile(INDEX, COMMITS, logId, version);
        myIsFresh = !commitsStorage.exists();
        commits = new PersistentSetImpl<>(commitsStorage, EnumeratorIntegerDescriptor.INSTANCE, Page.PAGE_SIZE, null, version);
        Disposer.register(disposable, () -> catchAndWarn(commits::close));

        File messagesStorage = getStorageFile(INDEX, MESSAGES, logId, VcsLogStorageImpl.VERSION + MESSAGES_VERSION);
        messages = new PersistentHashMap<>(messagesStorage, new IntInlineKeyDescriptor(), EnumeratorStringDescriptor.INSTANCE,
                                           Page.PAGE_SIZE);
        Disposer.register(disposable, () -> catchAndWarn(messages::close));

        trigrams = new VcsLogMessagesTrigramIndex(logId, fatalErrorHandler, disposable);
        users = new VcsLogUserIndex(logId, userRegistry, fatalErrorHandler, disposable);
        paths = new VcsLogPathsIndex(logId, roots, fatalErrorHandler, disposable);

        File parentsStorage = getStorageFile(INDEX, PARENTS, logId, version);
        parents = new PersistentHashMap<>(parentsStorage, EnumeratorIntegerDescriptor.INSTANCE,
                                          new IntListDataExternalizer(), Page.PAGE_SIZE, version);
        Disposer.register(disposable, () -> catchAndWarn(parents::close));
      }
      catch (Throwable t) {
        Disposer.dispose(disposable);
        throw t;
      }
    }

    void markCorrupted() {
      catchAndWarn(commits::markCorrupted);
    }

    private static void catchAndWarn(@NotNull ThrowableRunnable<IOException> runnable) {
      try {
        runnable.run();
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }

    private static void cleanup(@NotNull String logId) {
      if (!cleanupStorageFiles(INDEX, logId)) {
        LOG.error("Could not clean up storage files in " + new File(LOG_CACHE, INDEX) + " starting with " + logId);
      }
    }

    public void unmarkFresh() {
      myIsFresh = false;
    }

    public boolean isFresh() {
      return myIsFresh;
    }
  }

  private class MySingleTaskController extends SingleTaskController<IndexingRequest, Void> {
    public MySingleTaskController() {
      super(EmptyConsumer.getInstance(), false);
    }

    @NotNull
    @Override
    protected ProgressIndicator startNewBackgroundTask() {
      ProgressIndicator indicator = myProgress.createProgressIndicator(false);
      ApplicationManager.getApplication().invokeLater(() -> {
        Task.Backgroundable task = new Task.Backgroundable(VcsLogPersistentIndex.this.myProject, "Indexing Commit Data", true,
                                                           PerformInBackgroundOption.ALWAYS_BACKGROUND) {

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            IndexingRequest request;
            while ((request = popRequest()) != null) {
              try {
                request.run(indicator);
                indicator.checkCanceled();
              }
              catch (ProcessCanceledException reThrown) {
                taskCompleted(null);
                throw reThrown;
              }
              catch (Throwable t) {
                LOG.error("Error while indexing", t);
              }
            }
            taskCompleted(null);
          }
        };
        HeavyAwareExecutor.executeOutOfHeavyProcess(task, indicator);
      });
      return indicator;
    }
  }

  private class IndexingRequest {
    private static final int BATCH_SIZE = 20000;
    private static final int FLUSHED_COMMITS_NUMBER = 15000;
    @NotNull private final VirtualFile myRoot;
    @NotNull private final TIntHashSet myCommits;
    private final boolean myFull;

    public IndexingRequest(@NotNull VirtualFile root, @NotNull TIntHashSet commits, boolean full) {
      myRoot = root;
      myCommits = commits;
      myFull = full;
    }

    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setFraction(0);

      long startTime = System.currentTimeMillis();

      CommitsCounter counter = new CommitsCounter(indicator, myCommits.size());
      LOG.debug("Indexing " + counter.allCommits + " commits in " + myRoot.getName());

      try {
        IntStream commits = TroveUtil.stream(myCommits).filter(c -> {
          if (isIndexed(c)) {
            counter.oldCommits++;
            return false;
          }
          return true;
        });

        try {
          if (myFull) {
            indexAll(collect(commits), counter);
          }
          else {
            indexOneByOne(commits, counter);
          }
        }
        catch (ProcessCanceledException e) {
          scheduleReindex();
          throw e;
        }
        catch (VcsException e) {
          LOG.error(e);
          scheduleReindex();
        }
      }
      finally {
        myNumberOfTasks.get(myRoot).decrementAndGet();

        if (isIndexed(myRoot)) {
          myListeners.forEach(listener -> listener.indexingFinished(myRoot));
        }

        report(startTime, counter);
      }
    }

    private void report(long time, @NotNull CommitsCounter counter) {
      LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - time) +
                " for indexing " +
                counter.newIndexedCommits +
                " new commits out of " +
                counter.allCommits + " in " + myRoot.getName());
      int leftCommits = counter.allCommits - counter.newIndexedCommits - counter.oldCommits;
      if (leftCommits > 0) {
        LOG.warn("Did not index " + leftCommits + " commits");
      }
    }

    private void scheduleReindex() {
      LOG.debug("Schedule reindexing of " + myCommits.size() + " commits in " + myRoot.getName());
      myCommits.forEach(value -> {
        markForIndexing(value, myRoot);
        return true;
      });
      scheduleIndex(false);
    }

    private void indexOneByOne(@NotNull IntStream commits, @NotNull CommitsCounter counter) throws VcsException {
      // We pass hashes to VcsLogProvider#readFullDetails in batches
      // in order to avoid allocating too much memory for these hashes
      // a batch of 20k will occupy ~2.4Mb
      TroveUtil.processBatches(commits, BATCH_SIZE, batch -> {
        counter.indicator.checkCanceled();

        List<String> hashes = TroveUtil.map(batch, value -> myStorage.getCommitId(value).getHash().asString());
        myProviders.get(myRoot).readFullDetails(myRoot, hashes, VcsLogPersistentIndex.this::storeDetail, true);
        counter.newIndexedCommits += batch.size();

        counter.displayProgress();
      });

      flush();
    }

    public void indexAll(@NotNull TIntHashSet commits, @NotNull CommitsCounter counter) throws VcsException {
      counter.displayProgress();

      myProviders.get(myRoot).readAllFullDetails(myRoot, details -> {
        int index = myStorage.getCommitIndex(details.getId(), details.getRoot());
        if (commits.contains(index)) {
          storeDetail(details);
          counter.newIndexedCommits++;

          if (counter.newIndexedCommits % FLUSHED_COMMITS_NUMBER == 0) flush();
        }

        counter.indicator.checkCanceled();
        counter.displayProgress();
      });

      flush();
    }

    @Override
    public String toString() {
      return "IndexingRequest of " + myCommits.size() + " commits in " + myRoot.getName() + (myFull ? " (full)" : "");
    }
  }

  private static class CommitsCounter {
    @NotNull public final ProgressIndicator indicator;
    public final int allCommits;
    public volatile int newIndexedCommits;
    public volatile int oldCommits;

    private CommitsCounter(@NotNull ProgressIndicator indicator, int commits) {
      this.indicator = indicator;
      this.allCommits = commits;
    }

    public void displayProgress() {
      indicator.setFraction(((double)newIndexedCommits + oldCommits) / allCommits);
    }
  }
}
