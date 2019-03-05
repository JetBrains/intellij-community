// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author max
 */
public class FileWatcher {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  public static final NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
    @NotNull
    @Override
    protected NotificationGroup compute() {
      return new NotificationGroup("File Watcher Messages", NotificationDisplayType.STICKY_BALLOON, true);
    }
  };

  static class DirtyPaths {
    final Set<String> dirtyPaths = ContainerUtil.newTroveSet();
    final Set<String> dirtyPathsRecursive = ContainerUtil.newTroveSet();
    final Set<String> dirtyDirectories = ContainerUtil.newTroveSet();

    static final DirtyPaths EMPTY = new DirtyPaths();

    boolean isEmpty() {
      return dirtyPaths.isEmpty() && dirtyPathsRecursive.isEmpty() && dirtyDirectories.isEmpty();
    }

    private void addDirtyPath(@NotNull String path) {
      if (!dirtyPathsRecursive.contains(path)) {
        dirtyPaths.add(path);
      }
    }

    private void addDirtyPathRecursive(@NotNull String path) {
      dirtyPaths.remove(path);
      dirtyPathsRecursive.add(path);
    }
  }

  private final ManagingFS myManagingFS;
  private final MyFileWatcherNotificationSink myNotificationSink;
  private final PluggableFileWatcher[] myWatchers;
  private final AtomicBoolean myFailureShown = new AtomicBoolean(false);

  private volatile CanonicalPathMap myPathMap = new CanonicalPathMap();
  private volatile List<Collection<String>> myManualWatchRoots = Collections.emptyList();
  
  private final ExecutorService myFileWatcherExecutor = Registry.is("vfs.filewatcher.works.in.async.way") ?
                                                        AppExecutorUtil.createBoundedApplicationPoolExecutor("File Watcher", 1) :
                                                        MoreExecutors.newDirectExecutorService();

  FileWatcher(@NotNull ManagingFS managingFS) {
    myManagingFS = managingFS;
    myNotificationSink = new MyFileWatcherNotificationSink();
    myWatchers = PluggableFileWatcher.EP_NAME.getExtensions();

    myFileWatcherExecutor.submit(() -> {
      for (PluggableFileWatcher watcher : myWatchers) {
        watcher.initialize(myManagingFS, myNotificationSink);
      }
    });
  }

  public void dispose() {
    waitForFuture(myFileWatcherExecutor.submit(() -> {
      for (PluggableFileWatcher watcher : myWatchers) {
        watcher.dispose();
      }
    }));
  }

  public boolean isOperational() {
    for (PluggableFileWatcher watcher : myWatchers) {
      if (watcher.isOperational()) return true;
    }
    return false;
  }

  public boolean isSettingRoots() {
    flushCommandQueue();
    
    for (PluggableFileWatcher watcher : myWatchers) {
      if (watcher.isSettingRoots()) return true;
    }
    return false;
  }

  private void flushCommandQueue() {
    waitForFuture(myFileWatcherExecutor.submit(EmptyRunnable.getInstance()));
  }

  private void waitForFuture(@NotNull Future<?> future) {
    try {
      future.get();
    }
    catch (InterruptedException | ExecutionException ignore) {
    }
  }

  @NotNull
  DirtyPaths getDirtyPaths() {
    return myNotificationSink.getDirtyPaths();
  }

  @NotNull
  public Collection<String> getManualWatchRoots() {
    List<Collection<String>> manualWatchRoots = myManualWatchRoots;

    Set<String> result = null;
    for (Collection<String> roots : manualWatchRoots) {
      if (result == null) {
        result = ContainerUtil.newHashSet(roots);
      }
      else {
        result.retainAll(roots);
      }
    }

    return result != null ? result : Collections.emptyList();
  }

  /**
   * Clients should take care of not calling this method in parallel.
   */
  void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat) {
    myFileWatcherExecutor.submit(() -> {
      CanonicalPathMap pathMap = new CanonicalPathMap(recursive, flat);

      myPathMap = pathMap;
      myManualWatchRoots = ContainerUtil.createLockFreeCopyOnWriteList();

      for (PluggableFileWatcher watcher : myWatchers) {
        watcher.setWatchRoots(pathMap.getCanonicalRecursiveWatchRoots(), pathMap.getCanonicalFlatWatchRoots());
      }
    });
  }

  public void notifyOnFailure(@NotNull String cause, @Nullable NotificationListener listener) {
    LOG.warn(cause);

    if (myFailureShown.compareAndSet(false, true)) {
      NotificationGroup group = NOTIFICATION_GROUP.getValue();
      String title = ApplicationBundle.message("watcher.slow.sync");
      ApplicationManager.getApplication().invokeLater(
        () -> Notifications.Bus.notify(group.createNotification(title, cause, NotificationType.WARNING, listener)), ModalityState.NON_MODAL);
    }
  }

  private class MyFileWatcherNotificationSink implements FileWatcherNotificationSink {
    private final Object myLock = new Object();
    private DirtyPaths myDirtyPaths = new DirtyPaths();

    @NotNull
    DirtyPaths getDirtyPaths() {
      DirtyPaths dirtyPaths = DirtyPaths.EMPTY;

      synchronized (myLock) {
        if (!myDirtyPaths.isEmpty()) {
          dirtyPaths = myDirtyPaths;
          myDirtyPaths = new DirtyPaths();
        }
      }

      for (PluggableFileWatcher watcher : myWatchers) {
        watcher.resetChangedPaths();
      }

      return dirtyPaths;
    }

    @Override
    public void notifyManualWatchRoots(@NotNull Collection<String> roots) {
      myManualWatchRoots.add(roots.isEmpty() ? Collections.emptySet() : ContainerUtil.newHashSet(roots));
      notifyOnEvent(OTHER);
    }

    @Override
    public void notifyMapping(@NotNull Collection<? extends Pair<String, String>> mapping) {
      if (!mapping.isEmpty()) {
        myPathMap.addMapping(mapping);
      }
      notifyOnEvent(OTHER);
    }

    @Override
    public void notifyDirtyPath(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, true);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          for (String eachPath : paths) {
            myDirtyPaths.addDirtyPath(eachPath);
          }
        }
      }
      notifyOnEvent(path);
    }

    @Override
    public void notifyPathCreatedOrDeleted(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, true);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          for (String p : paths) {
            myDirtyPaths.addDirtyPathRecursive(p);
            String parentPath = new File(p).getParent();
            if (parentPath != null) {
              myDirtyPaths.addDirtyPath(parentPath);
            }
          }
        }
      }
      notifyOnEvent(path);
    }

    @Override
    public void notifyDirtyDirectory(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          myDirtyPaths.dirtyDirectories.addAll(paths);
        }
      }
      notifyOnEvent(path);
    }

    @Override
    public void notifyDirtyPathRecursive(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          for (String each : paths) {
            myDirtyPaths.addDirtyPathRecursive(each);
          }
        }
      }
      notifyOnEvent(path);
    }

    @Override
    public void notifyReset(@Nullable String path) {
      if (path != null) {
        synchronized (myLock) {
          myDirtyPaths.addDirtyPathRecursive(path);
        }
      }
      else {
        VirtualFile[] roots = myManagingFS.getLocalRoots();
        synchronized (myLock) {
          for (VirtualFile root : roots) {
            myDirtyPaths.addDirtyPathRecursive(root.getPresentableUrl());
          }
        }
      }
      notifyOnEvent(RESET);
    }

    @Override
    public void notifyUserOnFailure(@NotNull String cause, @Nullable NotificationListener listener) {
      notifyOnFailure(cause, listener);
    }
  }

  //<editor-fold desc="Test stuff.">
  public static final String RESET = "(reset)";
  public static final String OTHER = "(other)";

  private volatile Consumer<String> myTestNotifier;

  private void notifyOnEvent(String path) {
    Consumer<String> notifier = myTestNotifier;
    if (notifier != null) notifier.accept(path);
  }

  @TestOnly
  public void startup(@Nullable Consumer<String> notifier) throws Exception {
    myTestNotifier = notifier;
    myFileWatcherExecutor.submit(() -> {
      for (PluggableFileWatcher watcher : myWatchers) {
        watcher.startup();
      }
      return null;
    }).get();
  }

  @TestOnly
  public void shutdown() throws Exception {
    myFileWatcherExecutor.submit(() -> {
      for (PluggableFileWatcher watcher : myWatchers) {
        watcher.shutdown();
      }
      myTestNotifier = null;
      return null;
    }).get();
  }
  //</editor-fold>
}