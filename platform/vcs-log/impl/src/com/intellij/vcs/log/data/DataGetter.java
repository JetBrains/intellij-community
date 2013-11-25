package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
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
        preLoadCommitData(task.nodes);
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
  public T getCommitData(@NotNull final Node node) {
    assert EventQueue.isDispatchThread();
    Hash hash = myDataHolder.getHash(node.getCommitIndex());
    T details = getFromCache(hash);
    if (details != null) {
      return details;
    }
    return loadingDetails(node, hash);
  }

  @NotNull
  private T loadingDetails(Node node, Hash hash) {
    TaskDescriptor descriptor = runLoadAroundCommitData(node);
    T loadingDetails = (T)new LoadingDetails(hash, descriptor.getTaskNum(), node.getBranch().getRepositoryRoot());
    return loadingDetails;
  }

  @NotNull
  public T getCommitData(@NotNull Hash hash) {
    assert EventQueue.isDispatchThread();
    T details = getFromCache(hash);
    if (details != null) {
      return details;
    }
    Node node = myDataHolder.getDataPack().getNodeByHash(hash); // TODO this may possibly be slow => need to add to the Task as well
    return loadingDetails(node, hash);
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

  @Nullable
  private Node getCommitNodeInRow(int rowIndex) {
    Graph graph = myDataHolder.getDataPack().getGraphModel().getGraph();
    if (rowIndex < 0 || rowIndex >= graph.getNodeRows().size()) {
      return null;
    }
    NodeRow row = graph.getNodeRows().get(rowIndex);
    for (Node node : row.getNodes()) {
      if (node.getType() == Node.NodeType.COMMIT_NODE) {
        return node;
      }
    }
    return null;
  }

  @NotNull 
  private TaskDescriptor runLoadAroundCommitData(@NotNull Node node) {
    int rowIndex = node.getRowIndex();
    List<Node> nodes = new ArrayList<Node>();
    long taskNumber = myCurrentTaskIndex++;
    for (int i = rowIndex - UP_PRELOAD_COUNT; i < rowIndex + DOWN_PRELOAD_COUNT; i++) {
      Node commitNode = getCommitNodeInRow(i);
      if (commitNode != null) {
        nodes.add(commitNode);
        Hash hash = myDataHolder.getHash(commitNode.getCommitIndex());

        // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
        // even if it will be loaded within a previous query
        if (!myCache.isKeyCached(hash)) {
          myCache.put(hash, (T)new LoadingDetails(hash, taskNumber, commitNode.getBranch().getRepositoryRoot()));
        }
      }
    }
    TaskDescriptor task = new TaskDescriptor(nodes, taskNumber);
    myLoader.queue(task);
    return task;
  }

  private void preLoadCommitData(@NotNull List<Node> nodes) throws VcsException {
    MultiMap<VirtualFile, String> hashesByRoots = new MultiMap<VirtualFile, String>();
    for (Node node : nodes) {
      VirtualFile root = node.getBranch().getRepositoryRoot();
      hashesByRoots.putValue(root, myDataHolder.getHash(node.getCommitIndex()).asString());
    }

    for (Map.Entry<VirtualFile, Collection<String>> entry : hashesByRoots.entrySet()) {
      List<? extends T> details = readDetails(myLogProviders.get(entry.getKey()), entry.getKey(), new ArrayList<String>(entry.getValue()));
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
    private final List<Node> nodes;
    private long myTaskNum;

    private TaskDescriptor(List<Node> nodes, long taskNum) {
      this.nodes = nodes;
      myTaskNum = taskNum;
    }

    public long getTaskNum() {
      return myTaskNum;
    }
  }

}
