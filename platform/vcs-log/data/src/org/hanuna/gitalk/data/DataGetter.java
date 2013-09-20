package org.hanuna.gitalk.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogLogger;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
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
public abstract class DataGetter<T extends CommitParents> implements Disposable {

  private static final Logger LOG = VcsLogLogger.LOG;
  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;

  @NotNull protected final VcsLogDataHolder myDataHolder;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final VcsCommitCache<T> myCache;

  @NotNull private final QueueProcessor<TaskDescriptor> myLoader = new QueueProcessor<TaskDescriptor>(new DetailsLoadingTask());
  @NotNull private final Collection<Runnable> myLoadingFinishedListeners = new ArrayList<Runnable>();

  DataGetter(@NotNull VcsLogDataHolder dataHolder, @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
             @NotNull VcsCommitCache<T> cache) {
    myDataHolder = dataHolder;
    myLogProviders = logProviders;
    myCache = cache;
    Disposer.register(dataHolder, this);
  }

  @Override
  public void dispose() {
    myLoadingFinishedListeners.clear();
    myLoader.clear();
  }

  @NotNull
  public T getCommitData(final Node node) {
    assert EventQueue.isDispatchThread();
    Hash hash = node.getCommitHash();
    T details = myCache.get(hash);
    if (details != null) {
      return details;
    }

    T loadingDetails = (T)new LoadingDetails(hash);
    runLoadAroundCommitData(node);
    return loadingDetails;
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

  private void runLoadAroundCommitData(@NotNull Node node) {
    int rowIndex = node.getRowIndex();
    List<Node> nodes = new ArrayList<Node>();
    for (int i = rowIndex - UP_PRELOAD_COUNT; i < rowIndex + DOWN_PRELOAD_COUNT; i++) {
      Node commitNode = getCommitNodeInRow(i);
      if (commitNode != null) {
        nodes.add(commitNode);
        Hash hash = commitNode.getCommitHash();

        // fill the cache with temporary "Loading" values to avoid producing queries for each commit that has not been cached yet,
        // even if it will be loaded within a previous query
        if (!myCache.isKeyCached(hash)) {
          myCache.put(hash, (T)new LoadingDetails(hash));
        }
      }
    }
    myLoader.addFirst(new TaskDescriptor(nodes));
  }

  private void preLoadCommitData(@NotNull List<Node> nodes) throws VcsException {
    MultiMap<VirtualFile, String> hashesByRoots = new MultiMap<VirtualFile, String>();
    for (Node node : nodes) {
      VirtualFile root = node.getBranch().getRepositoryRoot();
      hashesByRoots.putValue(root, node.getCommitHash().toStrHash());
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

    private TaskDescriptor(List<Node> nodes) {
      this.nodes = nodes;
    }
  }

  private class DetailsLoadingTask implements Consumer<TaskDescriptor> {
    private static final int MAX_LOADINGS = 10;

    @Override
    public void consume(final TaskDescriptor task) {
      try {
        myLoader.dismissLastTasks(MAX_LOADINGS);
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
      catch (VcsException e) {
        throw new RuntimeException(e); // todo
      }
    }
  }
}
