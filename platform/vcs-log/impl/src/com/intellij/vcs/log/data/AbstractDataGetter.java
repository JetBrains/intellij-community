package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

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

  @NotNull protected final VcsLogHashMap myHashMap;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final VcsCommitCache<Integer, T> myCache;
  @NotNull private final SequentialLimitedLifoExecutor<TaskDescriptor> myLoader;

  /**
   * The sequence number of the current "loading" task.
   */
  private long myCurrentTaskIndex = 0;

  @NotNull private final Collection<Runnable> myLoadingFinishedListeners = new ArrayList<Runnable>();

  AbstractDataGetter(@NotNull VcsLogHashMap hashMap,
                     @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                     @NotNull VcsCommitCache<Integer, T> cache,
                     @NotNull Disposable parentDisposable) {
    myHashMap = hashMap;
    myLogProviders = logProviders;
    myCache = cache;
    Disposer.register(parentDisposable, this);
    myLoader =
      new SequentialLimitedLifoExecutor<TaskDescriptor>(this, MAX_LOADING_TASKS, new ThrowableConsumer<TaskDescriptor, VcsException>() {
        @Override
        public void consume(final TaskDescriptor task) throws VcsException {
          preLoadCommitData(task.myCommits);
          notifyLoaded();
        }
      });
  }

  private void notifyLoaded() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (Runnable loadingFinishedListener : myLoadingFinishedListeners) {
          loadingFinishedListener.run();
        }
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
    loadCommitsData(getCommitsMap(hashes), consumer, indicator);
  }

  private void loadCommitsData(@NotNull final Map<Integer, Integer> commits,
                               @NotNull final Consumer<List<T>> consumer,
                               @Nullable ProgressIndicator indicator) {
    final List<T> result = ContainerUtil.newArrayList();
    final Set<Integer> toLoad = ContainerUtil.newHashSet();

    long taskNumber = myCurrentTaskIndex++;

    for (Integer id : commits.keySet()) {
      VirtualFile root = myHashMap.getCommitId(id).getRoot();
      T details = getFromCache(id);
      if (details == null || details instanceof LoadingDetails) {
        toLoad.add(id);
        cacheCommit(id, root, taskNumber);
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
          public void run(@NotNull final ProgressIndicator indicator) {
            indicator.checkCanceled();
            try {
              result.addAll(preLoadCommitData(toLoad));
              sortCommitsByRow(result, commits);
              notifyLoaded();
            }
            catch (VcsException e) {
              LOG.error(e);
            }
          }

          @Override
          public void onSuccess() {
            consumer.consume(result);
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

  private void sortCommitsByRow(@NotNull List<T> result, @NotNull final Map<Integer, Integer> rowsForCommits) {
    ContainerUtil.sort(result, new Comparator<T>() {
      @Override
      public int compare(T details1, T details2) {
        Integer row1 = rowsForCommits.get(myHashMap.getCommitIndex(details1.getId(), details1.getRoot()));
        Integer row2 = rowsForCommits.get(myHashMap.getCommitIndex(details2.getId(), details2.getRoot()));
        return Comparing.compare(row1, row2);
      }
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
    Map<Integer, Integer> commits = getCommitsMap(hashes);
    Set<Integer> toLoad = ContainerUtil.newHashSet();

    for (Integer id : commits.keySet()) {
      VirtualFile root = myHashMap.getCommitId(id).getRoot();

      cacheCommit(id, root, taskNumber);
      toLoad.add(id);
    }

    myLoader.queue(new TaskDescriptor(toLoad));
  }

  private void cacheCommit(final int commitId, VirtualFile root, long taskNumber) {
    // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
    // even if it will be loaded within a previous query
    if (!myCache.isKeyCached(commitId)) {
      myCache.put(commitId, (T)new LoadingDetails(new Computable<Hash>() {

        @Override
        public Hash compute() {
          return myHashMap.getCommitId(commitId).getHash();
        }
      }, taskNumber, root));
    }
  }

  @NotNull
  private static Map<Integer, Integer> getCommitsMap(@NotNull Iterable<Integer> hashes) {
    Map<Integer, Integer> commits = ContainerUtil.newHashMap();
    int row = 0;
    for (Integer commitId : hashes) {
      commits.put(commitId, row);
      row++;
    }
    return commits;
  }

  private Set<T> preLoadCommitData(@NotNull Set<Integer> commits) throws VcsException {
    Set<T> result = ContainerUtil.newHashSet();
    MultiMap<VirtualFile, String> rootsAndHashes = MultiMap.create();
    for (Integer commit : commits) {
      CommitId commitId = myHashMap.getCommitId(commit);
      rootsAndHashes.putValue(commitId.getRoot(), commitId.getHash().asString());
    }

    for (Map.Entry<VirtualFile, Collection<String>> entry : rootsAndHashes.entrySet()) {
      VcsLogProvider logProvider = myLogProviders.get(entry.getKey());
      if (logProvider != null) {
        List<? extends T> details = readDetails(logProvider, entry.getKey(), ContainerUtil.newArrayList(entry.getValue()));
        result.addAll(details);
        saveInCache(details);
      } else {
        LOG.error("No log provider for root " + entry.getKey().getPath() + ". All known log providers " + myLogProviders);
      }
    }

    return result;
  }

  public void saveInCache(final List<? extends T> details) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (T data : details) {
          myCache.put(myHashMap.getCommitIndex(data.getId(), data.getRoot()), data);
        }
      }
    });
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
    @NotNull private final Set<Integer> myCommits;

    private TaskDescriptor(@NotNull Set<Integer> commits) {
      myCommits = commits;
    }
  }
}
