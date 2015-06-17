package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
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

  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;
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
  public T getCommitData(int row, @NotNull GraphTableModel tableModel) {
    assert EventQueue.isDispatchThread();
    Integer hash = tableModel.getIdAtRow(row);
    T details = getFromCache(hash);
    if (details != null) {
      return details;
    }

    runLoadCommitsData(tableModel, createRowsIterable(row, UP_PRELOAD_COUNT, DOWN_PRELOAD_COUNT, tableModel.getRowCount()));

    T result = myCache.get(hash);
    assert result != null; // now it is in the cache as "Loading Details" (runLoadCommitsData puts it there)
    return result;
  }

  private static Iterable<Integer> createRowsIterable(final int row, final int above, final int below, final int maxRows) {
    return new Iterable<Integer>() {
      @NotNull
      @Override
      public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
          private int myIndex = Math.max(0, row - above);

          @Override
          public boolean hasNext() {
            return myIndex < row + below && myIndex < maxRows;
          }

          @Override
          public Integer next() {
            int next = myIndex;
            myIndex++;
            return next;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException("Removing elements is not supported.");
          }
        };
      }
    };
  }

  @Override
  public void loadCommitsData(@NotNull List<Integer> rows,
                              @NotNull GraphTableModel tableModel,
                              @NotNull Consumer<Set<T>> consumer,
                              @Nullable ProgressIndicator indicator) {
    assert EventQueue.isDispatchThread();
    loadCommitsData(getCommitsForRows(rows, tableModel), consumer, indicator);
  }

  private void loadCommitsData(@NotNull final MultiMap<VirtualFile, Integer> commits,
                               @NotNull final Consumer<Set<T>> consumer,
                               @Nullable ProgressIndicator indicator) {
    final Set<T> result = ContainerUtil.newHashSet();
    final MultiMap<VirtualFile, Integer> toLoad = MultiMap.create();

    long taskNumber = myCurrentTaskIndex++;

    for (VirtualFile root : commits.keySet()) {
      Collection<Integer> hashesForRoot = commits.get(root);
      for (final Integer commitId : hashesForRoot) {
        T details = getFromCache(commitId);
        if (details == null || details instanceof LoadingDetails) {
          toLoad.putValue(root, commitId);
          cacheCommit(commitId, root, taskNumber);
        }
        else {
          result.add(details);
        }
      }
    }

    if (toLoad.isEmpty()) {
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

  private void runLoadCommitsData(@NotNull GraphTableModel tableModel, @NotNull Iterable<Integer> rows) {
    long taskNumber = myCurrentTaskIndex++;
    MultiMap<VirtualFile, Integer> commits = getCommitsForRows(rows, tableModel);
    for (Map.Entry<VirtualFile, Collection<Integer>> hashesByRoots : commits.entrySet()) {
      VirtualFile root = hashesByRoots.getKey();
      Collection<Integer> hashes = hashesByRoots.getValue();

      for (final int commitId : hashes) {
        cacheCommit(commitId, root, taskNumber);
      }
    }

    myLoader.queue(new TaskDescriptor(commits));
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
  private static MultiMap<VirtualFile, Integer> getCommitsForRows(@NotNull Iterable<Integer> rows, @NotNull GraphTableModel model) {
    MultiMap<VirtualFile, Integer> commits = MultiMap.create();
    for (int row : rows) {
      Integer commitId = model.getIdAtRow(row);
      VirtualFile root = model.getRoot(row);
      commits.putValue(root, commitId);
    }
    return commits;
  }

  private Set<T> preLoadCommitData(@NotNull MultiMap<VirtualFile, Integer> commits) throws VcsException {
    Set<T> result = ContainerUtil.newHashSet();
    for (Map.Entry<VirtualFile, Collection<Integer>> entry : commits.entrySet()) {
      List<String> hashStrings = ContainerUtil.map(entry.getValue(), new Function<Integer, String>() {
        @Override
        public String fun(Integer commitId) {
          return myHashMap.getCommitId(commitId).getHash().asString();
        }
      });
      List<? extends T> details = readDetails(myLogProviders.get(entry.getKey()), entry.getKey(), hashStrings);
      result.addAll(details);
      saveInCache(details);
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
  protected abstract List<? extends T> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                   @NotNull List<String> hashes) throws VcsException;

  /**
   * This listener will be notified when any details loading process finishes.
   * The notification will happen in the EDT.
   */
  public void addDetailsLoadedListener(@NotNull Runnable runnable) {
    myLoadingFinishedListeners.add(runnable);
  }

  private static class TaskDescriptor {
    @NotNull private final MultiMap<VirtualFile, Integer> myCommits;

    private TaskDescriptor(@NotNull MultiMap<VirtualFile, Integer> commits) {
      myCommits = commits;
    }
  }
}
