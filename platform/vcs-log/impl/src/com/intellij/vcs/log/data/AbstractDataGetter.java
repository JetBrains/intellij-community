// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * The DataGetter realizes the following pattern of getting some data (parametrized by {@code T}) from the VCS:
 * <ul>
 * <li>it tries to get it from the cache;</li>
 * <li>if it fails, it tries to get it from the VCS, and additionally loads several commits around the requested one,
 * to avoid querying the VCS if user investigates details of nearby commits.</li>
 * <li>The loading happens asynchronously: a fake {@link LoadingDetails} object is returned </li>
 * </ul>
 *
 * @author Kirill Likhodedov
 */
abstract class AbstractDataGetter<T extends VcsShortCommitDetails> implements Disposable, DataGetter<T> {
  private static final Logger LOG = Logger.getInstance(AbstractDataGetter.class);

  private static final int MAX_LOADING_TASKS = 10;

  @NotNull protected final VcsLogStorage myStorage;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final Cache<Integer, T> myCache = Caffeine.newBuilder().maximumSize(10_000).build();
  @NotNull private final SequentialLimitedLifoExecutor<TaskDescriptor> myLoader;

  /**
   * The sequence number of the current "loading" task.
   */
  private long myCurrentTaskIndex = 0;

  @NotNull private final Collection<Runnable> myLoadingFinishedListeners = new ArrayList<>();
  @NotNull protected final VcsLogIndex myIndex;

  AbstractDataGetter(@NotNull VcsLogStorage storage,
                     @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                     @NotNull VcsLogIndex index,
                     @NotNull Disposable parentDisposable) {
    myStorage = storage;
    myLogProviders = logProviders;
    myIndex = index;
    Disposer.register(parentDisposable, this);
    myLoader =
      new SequentialLimitedLifoExecutor<>(this, MAX_LOADING_TASKS, task -> {
        preLoadCommitData(task.myCommits, EmptyConsumer.getInstance());
        notifyLoaded();
      });
  }

  protected void notifyLoaded() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      for (Runnable loadingFinishedListener : myLoadingFinishedListeners) {
        loadingFinishedListener.run();
      }
    });
  }

  @Override
  public void dispose() {
    myLoadingFinishedListeners.clear();
  }

  @Override
  public @NotNull T getCommitData(int hash) {
    return getCommitData(hash, Collections.singleton(hash));
  }

  @NotNull
  public T getCommitData(int hash, @NotNull Iterable<Integer> neighbourHashes) {
    if (!EventQueue.isDispatchThread()) {
      LOG.warn("Accessing AbstractDataGetter from background thread");
      T commitFromCache = getFromCache(hash);
      if (commitFromCache == null) return createPlaceholderCommit(hash, 0 /*not used as this commit is not cached*/);
      return commitFromCache;
    }

    T details = getCommitDataIfAvailable(hash);
    if (details != null) {
      return details;
    }

    runLoadCommitsData(neighbourHashes);

    T result = getFromCache(hash);
    assert result != null; // now it is in the cache as "Loading Details" (runLoadCommitsData puts it there)
    return result;
  }

  @Override
  public void loadCommitsData(@NotNull List<Integer> hashes, @NotNull Consumer<? super List<T>> consumer,
                              @NotNull Consumer<? super Throwable> errorConsumer, @Nullable ProgressIndicator indicator) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    loadCommitsData(getCommitsMap(hashes), consumer, errorConsumer, indicator);
  }

  private void loadCommitsData(@NotNull Int2IntMap commits,
                               @NotNull Consumer<? super List<T>> consumer,
                               @NotNull Consumer<? super Throwable> errorConsumer,
                               @Nullable ProgressIndicator indicator) {
    final List<T> result = new ArrayList<>();
    final IntSet toLoad = new IntOpenHashSet();

    long taskNumber = myCurrentTaskIndex++;

    IntIterator keyIterator = commits.keySet().iterator();
    while (keyIterator.hasNext()) {
      int id = keyIterator.nextInt();
      T details = getCommitDataIfAvailable(id);
      if (details == null || details instanceof LoadingDetails) {
        toLoad.add(id);
        cacheCommit(id, taskNumber);
      }
      else {
        result.add(details);
      }
    }

    if (toLoad.isEmpty()) {
      myCurrentTaskIndex--;
      Runnable process = () -> {
        sortCommitsByRow(result, commits);
        consumer.consume(result);
      };
      if (indicator != null) {
        ProgressManager.getInstance().runProcess(process, indicator);
      }
      else {
        process.run();
      }
    }
    else {
      Task.Backgroundable task = new Task.Backgroundable(null,
                                                         VcsLogBundle.message("vcs.log.loading.selected.details.process"),
                                                         true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.checkCanceled();
          try {
            preLoadCommitData(toLoad, new CollectConsumer<>(result));
            sortCommitsByRow(result, commits);
            notifyLoaded();
          }
          catch (VcsException e) {
            LOG.warn(e);
            throw new RuntimeException(e);
          }
        }

        @Override
        public void onSuccess() {
          consumer.consume(result);
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          errorConsumer.consume(error);
        }
      };
      if (indicator != null) {
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
      }
      else {
        ProgressManager.getInstance().run(task);
      }
    }
  }

  private void sortCommitsByRow(@NotNull List<? extends T> result, @NotNull Int2IntMap rowsForCommits) {
    result.sort((details1, details2) -> {
      int row1 = rowsForCommits.get(myStorage.getCommitIndex(details1.getId(), details1.getRoot()));
      int row2 = rowsForCommits.get(myStorage.getCommitIndex(details2.getId(), details2.getRoot()));
      return Comparing.compare(row1, row2);
    });
  }

  @Override
  @Nullable
  public T getCommitDataIfAvailable(int hash) {
    LOG.assertTrue(EventQueue.isDispatchThread());
    T details = getFromCache(hash);
    if (details != null) {
      if (details instanceof LoadingDetailsImpl) {
        if (((LoadingDetailsImpl)details).getLoadingTaskIndex() <= myCurrentTaskIndex - MAX_LOADING_TASKS) {
          // don't let old "loading" requests stay in the cache forever
          myCache.asMap().remove(hash, details);
          return null;
        }
      }
      return details;
    }
    return getFromAdditionalCache(hash);
  }

  protected @Nullable T getFromCache(int hash) {
    return myCache.getIfPresent(hash);
  }

  /**
   * Lookup somewhere else but the standard cache.
   */
  @Nullable
  protected abstract T getFromAdditionalCache(int commitId);

  private void runLoadCommitsData(@NotNull Iterable<Integer> hashes) {
    long taskNumber = myCurrentTaskIndex++;
    Int2IntMap commits = getCommitsMap(hashes);
    IntSet toLoad = new IntOpenHashSet();

    IntIterator iterator = commits.keySet().iterator();
    while (iterator.hasNext()) {
      int id = iterator.nextInt();
      cacheCommit(id, taskNumber);
      toLoad.add(id);
    }

    myLoader.queue(new TaskDescriptor(toLoad));
  }

  private void cacheCommit(final int commitId, long taskNumber) {
    // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
    // even if it will be loaded within a previous query
    if (getFromCache(commitId) == null) {
      myCache.put(commitId, createPlaceholderCommit(commitId, taskNumber));
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private T createPlaceholderCommit(int commitId, long taskNumber) {
    IndexDataGetter dataGetter = myIndex.getDataGetter();
    if (dataGetter != null && Registry.is("vcs.log.use.indexed.details")) {
      return (T)new IndexedDetails(dataGetter, myStorage, commitId, taskNumber);
    }
    else {
      return (T)new LoadingDetailsImpl(() -> myStorage.getCommitId(commitId), taskNumber);
    }
  }

  private static @NotNull Int2IntMap getCommitsMap(@NotNull Iterable<Integer> hashes) {
    Int2IntMap commits = new Int2IntOpenHashMap();
    int row = 0;
    for (Integer commitId : hashes) {
      commits.put(commitId.intValue(), row);
      row++;
    }
    return commits;
  }

  protected void preLoadCommitData(@NotNull IntSet commits, @NotNull Consumer<? super T> consumer) throws VcsException {
    final MultiMap<VirtualFile, String> rootsAndHashes = MultiMap.create();
    commits.forEach(commit -> {
      CommitId commitId = myStorage.getCommitId(commit);
      if (commitId != null) {
        rootsAndHashes.putValue(commitId.getRoot(), commitId.getHash().asString());
      }
    });

    for (Map.Entry<VirtualFile, Collection<String>> entry : rootsAndHashes.entrySet()) {
      VcsLogProvider logProvider = myLogProviders.get(entry.getKey());
      if (logProvider != null) {
        readDetails(logProvider, entry.getKey(), new ArrayList<>(entry.getValue()), (details) -> {
          saveInCache(myStorage.getCommitIndex(details.getId(), details.getRoot()), details);
          consumer.consume(details);
        });
      }
      else {
        LOG.error("No log provider for root " + entry.getKey().getPath() + ". All known log providers " + myLogProviders);
      }
    }
  }

  protected abstract void readDetails(@NotNull VcsLogProvider logProvider,
                                      @NotNull VirtualFile root,
                                      @NotNull List<String> hashes,
                                      @NotNull Consumer<? super T> consumer) throws VcsException;

  protected void saveInCache(int index, @NotNull T details) {
    myCache.put(index, details);
  }

  protected void clear() {
    EdtInvocationManager.invokeAndWaitIfNeeded(() -> {
      Iterator<Map.Entry<Integer, T>> iterator = myCache.asMap().entrySet().iterator();
      while (iterator.hasNext()) {
        if (!(iterator.next().getValue() instanceof LoadingDetails)) {
          iterator.remove();
        }
      }
    });
  }

  /**
   * This listener will be notified when any details loading process finishes.
   * The notification will happen in the EDT.
   */
  public void addDetailsLoadedListener(@NotNull Runnable runnable) {
    myLoadingFinishedListeners.add(runnable);
  }

  public void removeDetailsLoadedListener(@NotNull Runnable runnable) {
    myLoadingFinishedListeners.remove(runnable);
  }

  private static final class TaskDescriptor {
    @NotNull private final IntSet myCommits;

    private TaskDescriptor(@NotNull IntSet commits) {
      myCommits = commits;
    }
  }
}
