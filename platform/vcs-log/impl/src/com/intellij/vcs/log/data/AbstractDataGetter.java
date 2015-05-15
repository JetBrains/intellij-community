package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import com.intellij.vcs.log.util.SequentialLimitedLifoExecutor;
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
 *   <li>it tries to get it from the cache;</li>
 *   <li>if it fails, it tries to get it from the VCS, and additionally loads several commits around the requested one,
 *       to avoid querying the VCS if user investigates details of nearby commits.</li>
 *   <li>The loading happens asynchronously: a fake {@link LoadingDetails} object is returned </li>
 * </ul>
 *
 * @author Kirill Likhodedov
 */
abstract class AbstractDataGetter<T extends VcsShortCommitDetails> implements Disposable, DataGetter<T> {

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
    myLoader = new SequentialLimitedLifoExecutor<TaskDescriptor>(this, MAX_LOADING_TASKS,
                                                                 new ThrowableConsumer<TaskDescriptor, VcsException>() {
      @Override
      public void consume(TaskDescriptor task) throws VcsException {
        preLoadCommitData(task.myCommits);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            for (Runnable loadingFinishedListener : myLoadingFinishedListeners) {
              loadingFinishedListener.run();
            }
          }
        });
      }
    });
  }

  @Override
  public void dispose() {
    myLoadingFinishedListeners.clear();
  }

  @Override
  @Nullable
  public T getCommitData(int row, @NotNull GraphTableModel tableModel) {
    assert EventQueue.isDispatchThread();
    Integer hash = tableModel.getCommitIdAtRow(row);
    T details = getFromCache(hash);
    if (details != null) {
      return details;
    }
    runLoadAroundCommitData(row, tableModel);
    return myCache.get(hash); // now it is in the cache as "Loading Details".
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

  private void runLoadAroundCommitData(int row, @NotNull GraphTableModel tableModel) {
    long taskNumber = myCurrentTaskIndex++;
    MultiMap<VirtualFile, Integer> commits = getCommitsAround(row, tableModel, UP_PRELOAD_COUNT, DOWN_PRELOAD_COUNT);
    for (Map.Entry<VirtualFile, Collection<Integer>> hashesByRoots : commits.entrySet()) {
      VirtualFile root = hashesByRoots.getKey();
      Collection<Integer> hashes = hashesByRoots.getValue();

      // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
      // even if it will be loaded within a previous query
      for (int commitId : hashes) {
        if (!myCache.isKeyCached(commitId)) {
          myCache.put(commitId, (T)new LoadingDetails(myHashMap.getHash(commitId), taskNumber, root));
        }
      }
    }

    TaskDescriptor task = new TaskDescriptor(commits);
    myLoader.queue(task);
  }

  @NotNull
  private static MultiMap<VirtualFile, Integer> getCommitsAround(int selectedRow,
                                                                 @NotNull GraphTableModel model,
                                                                 int above,
                                                                 int below) {
    MultiMap<VirtualFile, Integer> commits = MultiMap.create();
    for (int row = Math.max(0, selectedRow - above); row < selectedRow + below && row < model.getRowCount(); row++) {
      Integer hash = model.getCommitIdAtRow(row);
      VirtualFile root = model.getRoot(row);
      commits.putValue(root, hash);
    }
    return commits;
  }

  private void preLoadCommitData(@NotNull MultiMap<VirtualFile, Integer> commits) throws VcsException {
    for (Map.Entry<VirtualFile, Collection<Integer>> entry : commits.entrySet()) {
      List<String> hashStrings = ContainerUtil.map(entry.getValue(), new Function<Integer, String>() {
        @Override
        public String fun(Integer commitId) {
          return myHashMap.getHash(commitId).asString();
        }
      });
      List<? extends T> details = readDetails(myLogProviders.get(entry.getKey()), entry.getKey(), hashStrings);
      saveInCache(details);
    }
  }

  public void saveInCache(final List<? extends T> details) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (T data : details) {
          myCache.put(myHashMap.getCommitIndex(data.getId()), data);
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
    private final MultiMap<VirtualFile, Integer> myCommits;

    private TaskDescriptor(MultiMap<VirtualFile, Integer> commits) {
      myCommits = commits;
    }
  }

}
