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
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
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
public abstract class DataGetter<T extends VcsShortCommitDetails> implements Disposable {

  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;
  private static final int MAX_LOADING_TASKS = 10;

  @NotNull protected final VcsLogDataHolder myDataHolder;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final VcsCommitCache<T> myCache;
  @NotNull private final SequentialLimitedLifoExecutor<TaskDescriptor> myLoader;

  /**
   * The sequence number of the current "loading" task.
   */
  private long myCurrentTaskIndex = 0;

  @NotNull private final Collection<Runnable> myLoadingFinishedListeners = new ArrayList<Runnable>();

  DataGetter(@NotNull VcsLogDataHolder dataHolder, @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
             @NotNull VcsCommitCache<T> cache) {
    myDataHolder = dataHolder;
    myLogProviders = logProviders;
    myCache = cache;
    Disposer.register(dataHolder, this);
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

  @NotNull
  public <CommitId> T getCommitData(@NotNull CommitId id, @NotNull AroundProvider<CommitId> aroundProvider) {
    assert EventQueue.isDispatchThread();
    Hash hash = aroundProvider.resolveId(id);
    T details = getFromCache(hash);
    if (details != null) {
      return details;
    }
    runLoadAroundCommitData(id, aroundProvider);
    return myCache.get(hash); // now it is in the cache as "Loading Details".
  }

  @Nullable
  public T getCommitDataIfAvailable(@NotNull Hash hash) {
    return getFromCache(hash);
  }

  @Nullable
  private T getFromCache(@NotNull Hash hash) {
    T details = myCache.get(hash);
    if (details != null) {
      if (details instanceof LoadingDetails) {
        if (((LoadingDetails)details).getLoadingTaskIndex() <= myCurrentTaskIndex - MAX_LOADING_TASKS) {
          // don't let old "loading" requests stay in the cache forever
          myCache.remove(hash);
          return null;
        }
      }
      return details;
    }
    return (T)myDataHolder.getTopCommitDetails(hash);
  }

  private <CommitId> void runLoadAroundCommitData(@NotNull CommitId id, @NotNull AroundProvider<CommitId> aroundProvider) {
    long taskNumber = myCurrentTaskIndex++;
    MultiMap<VirtualFile, Hash> commits = aroundProvider.getCommitsAround(id, UP_PRELOAD_COUNT, DOWN_PRELOAD_COUNT);
    for (Map.Entry<VirtualFile, Collection<Hash>> hashesByRoots : commits.entrySet()) {
      VirtualFile root = hashesByRoots.getKey();
      Collection<Hash> hashes = hashesByRoots.getValue();

      // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
      // even if it will be loaded within a previous query
      for (Hash hash : hashes) {
        if (!myCache.isKeyCached(hash)) {
          myCache.put(hash, (T)new LoadingDetails(hash, taskNumber, root));
        }
      }
    }

    TaskDescriptor task = new TaskDescriptor(commits);
    myLoader.queue(task);
  }

  private void preLoadCommitData(@NotNull MultiMap<VirtualFile, Hash> commits) throws VcsException {
    for (Map.Entry<VirtualFile, Collection<Hash>> entry : commits.entrySet()) {
      List<String> hashStrings = ContainerUtil.map(entry.getValue(), new Function<Hash, String>() {
        @Override
        public String fun(Hash hash) {
          return hash.asString();
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
          myCache.put(data.getHash(), data);
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
    private final MultiMap<VirtualFile, Hash> myCommits;

    private TaskDescriptor(MultiMap<VirtualFile, Hash> commits) {
      myCommits = commits;
    }
  }

}
