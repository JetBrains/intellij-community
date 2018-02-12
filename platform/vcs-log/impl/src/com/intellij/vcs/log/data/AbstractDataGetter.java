package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
  @NotNull private final VcsCommitCache<Integer, T> myCache;
  @NotNull private final SequentialLimitedLifoExecutor<TaskDescriptor> myLoader;

  /**
   * The sequence number of the current "loading" task.
   */
  private long myCurrentTaskIndex = 0;

  @NotNull private final Collection<Runnable> myLoadingFinishedListeners = new ArrayList<>();
  @NotNull private final VcsLogIndex myIndex;

  AbstractDataGetter(@NotNull VcsLogStorage storage,
                     @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                     @NotNull VcsCommitCache<Integer, T> cache,
                     @NotNull VcsLogIndex index,
                     @NotNull Disposable parentDisposable) {
    myStorage = storage;
    myLogProviders = logProviders;
    myCache = cache;
    myIndex = index;
    Disposer.register(parentDisposable, this);
    myLoader =
      new SequentialLimitedLifoExecutor<>(this, MAX_LOADING_TASKS, task -> {
        preLoadCommitData(task.myCommits);
        notifyLoaded();
      });
  }

  private void notifyLoaded() {
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
  @NotNull
  public T getCommitData(@NotNull Integer hash, @NotNull Iterable<Integer> neighbourHashes) {
    assert EventQueue.isDispatchThread();
    T details = getFromCache(hash);
    if (details != null) {
      return details;
    }

    runLoadCommitsData(neighbourHashes);

    T result = myCache.get(hash);
    assert result != null; // now it is in the cache as "Loading Details" (runLoadCommitsData puts it there)
    return result;
  }

  @Override
  public void loadCommitsData(@NotNull List<Integer> hashes, @NotNull Consumer<List<T>> consumer, @Nullable ProgressIndicator indicator) {
    assert EventQueue.isDispatchThread();
    loadCommitsData(hashes, consumer, Consumer.EMPTY_CONSUMER, indicator);
  }

  public void loadCommitsData(@NotNull List<Integer> hashes, @NotNull Consumer<List<T>> consumer,
                              @NotNull Consumer<Throwable> errorConsumer, @Nullable ProgressIndicator indicator) {
    assert EventQueue.isDispatchThread();
    loadCommitsData(getCommitsMap(hashes), consumer, errorConsumer, indicator);
  }

  private void loadCommitsData(@NotNull TIntIntHashMap commits,
                               @NotNull Consumer<List<T>> consumer,
                               @NotNull Consumer<Throwable> errorConsumer,
                               @Nullable ProgressIndicator indicator) {
    final List<T> result = ContainerUtil.newArrayList();
    final TIntHashSet toLoad = new TIntHashSet();

    long taskNumber = myCurrentTaskIndex++;

    for (int id : commits.keys()) {
      T details = getFromCache(id);
      if (details == null || details instanceof LoadingDetails) {
        toLoad.add(id);
        cacheCommit(id, taskNumber);
      }
      else {
        result.add(details);
      }
    }

    if (toLoad.isEmpty()) {
      sortCommitsByRow(result, commits);
      consumer.consume(result);
    }
    else {
      Task.Backgroundable task =
        new Task.Backgroundable(null, "Loading Selected Details", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            indicator.checkCanceled();
            try {
              TIntObjectHashMap<T> map = preLoadCommitData(toLoad);
              map.forEachValue(value -> {
                result.add(value);
                return true;
              });
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

  private void sortCommitsByRow(@NotNull List<T> result, @NotNull final TIntIntHashMap rowsForCommits) {
    ContainerUtil.sort(result, (details1, details2) -> {
      int row1 = rowsForCommits.get(myStorage.getCommitIndex(details1.getId(), details1.getRoot()));
      int row2 = rowsForCommits.get(myStorage.getCommitIndex(details2.getId(), details2.getRoot()));
      return Comparing.compare(row1, row2);
    });
  }

  @Override
  @Nullable
  public T getCommitDataIfAvailable(int hash) {
    return getFromCache(hash);
  }

  @Nullable
  private T getFromCache(@NotNull Integer commitId) {
    T details = myCache.get(commitId);
    if (details != null) {
      if (details instanceof LoadingDetails) {
        if (((LoadingDetails)details).getLoadingTaskIndex() <= myCurrentTaskIndex - MAX_LOADING_TASKS) {
          // don't let old "loading" requests stay in the cache forever
          myCache.remove(commitId);
          return null;
        }
      }
      return details;
    }
    return getFromAdditionalCache(commitId);
  }

  /**
   * Lookup somewhere else but the standard cache.
   */
  @Nullable
  protected abstract T getFromAdditionalCache(int commitId);

  private void runLoadCommitsData(@NotNull Iterable<Integer> hashes) {
    long taskNumber = myCurrentTaskIndex++;
    TIntIntHashMap commits = getCommitsMap(hashes);
    TIntHashSet toLoad = new TIntHashSet();

    for (int id : commits.keys()) {
      cacheCommit(id, taskNumber);
      toLoad.add(id);
    }

    myLoader.queue(new TaskDescriptor(toLoad));
  }

  @SuppressWarnings("unchecked")
  private void cacheCommit(final int commitId, long taskNumber) {
    // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
    // even if it will be loaded within a previous query
    if (!myCache.isKeyCached(commitId)) {
      IndexDataGetter dataGetter = myIndex.getDataGetter();
      if (dataGetter != null) {
        myCache.put(commitId, (T)new IndexedDetails(dataGetter, myStorage, commitId, taskNumber));
      }
      else {
        myCache.put(commitId, (T)new LoadingDetails(() -> myStorage.getCommitId(commitId), taskNumber));
      }
    }
  }

  @NotNull
  private static TIntIntHashMap getCommitsMap(@NotNull Iterable<Integer> hashes) {
    TIntIntHashMap commits = new TIntIntHashMap();
    int row = 0;
    for (Integer commitId : hashes) {
      commits.put(commitId, row);
      row++;
    }
    return commits;
  }

  @NotNull
  public TIntObjectHashMap<T> preLoadCommitData(@NotNull TIntHashSet commits) throws VcsException {
    TIntObjectHashMap<T> result = new TIntObjectHashMap<>();
    final MultiMap<VirtualFile, String> rootsAndHashes = MultiMap.create();
    commits.forEach(commit -> {
      CommitId commitId = myStorage.getCommitId(commit);
      if (commitId != null) {
        rootsAndHashes.putValue(commitId.getRoot(), commitId.getHash().asString());
      }
      return true;
    });

    for (Map.Entry<VirtualFile, Collection<String>> entry : rootsAndHashes.entrySet()) {
      VcsLogProvider logProvider = myLogProviders.get(entry.getKey());
      if (logProvider != null) {
        List<? extends T> details = readDetails(logProvider, entry.getKey(), ContainerUtil.newArrayList(entry.getValue()));
        for (T data : details) {
          int index = myStorage.getCommitIndex(data.getId(), data.getRoot());
          result.put(index, data);
        }
        saveInCache(result);
      }
      else {
        LOG.error("No log provider for root " + entry.getKey().getPath() + ". All known log providers " + myLogProviders);
      }
    }

    return result;
  }

  public void saveInCache(@NotNull TIntObjectHashMap<T> details) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> details.forEachEntry((key, value) -> {
      myCache.put(key, value);
      return true;
    }));
  }

  @NotNull
  protected abstract List<? extends T> readDetails(@NotNull VcsLogProvider logProvider,
                                                   @NotNull VirtualFile root,
                                                   @NotNull List<String> hashes) throws VcsException;

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

  private static class TaskDescriptor {
    @NotNull private final TIntHashSet myCommits;

    private TaskDescriptor(@NotNull TIntHashSet commits) {
      myCommits = commits;
    }
  }
}
