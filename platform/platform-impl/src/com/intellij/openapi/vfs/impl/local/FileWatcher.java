// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.application.options.RegistryManager;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unless stated otherwise, all paths are {@link SystemDependent @SystemDependent}.
 */
public final class FileWatcher {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  public static final NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
    @Override
    protected @NotNull NotificationGroup compute() {
      return new NotificationGroup("File Watcher Messages", NotificationDisplayType.STICKY_BALLOON, true);
    }
  };

  final static class DirtyPaths {
    final Set<String> dirtyPaths = new THashSet<>();
    final Set<String> dirtyPathsRecursive = new THashSet<>();
    final Set<String> dirtyDirectories = new THashSet<>();

    static final DirtyPaths EMPTY = new DirtyPaths();

    boolean isEmpty() {
      return dirtyPaths.isEmpty() && dirtyPathsRecursive.isEmpty() && dirtyDirectories.isEmpty();
    }

    void addDirtyPath(String path) {
      if (!dirtyPathsRecursive.contains(path)) {
        dirtyPaths.add(path);
      }
    }

    void addDirtyPathRecursive(String path) {
      dirtyPaths.remove(path);
      dirtyPathsRecursive.add(path);
    }
  }

  private static @NotNull ExecutorService executor() {
    boolean async = RegistryManager.getInstance().is("vfs.filewatcher.works.in.async.way");
    return async ? AppExecutorUtil.createBoundedApplicationPoolExecutor("File Watcher", 1) : ConcurrencyUtil.newSameThreadExecutorService();
  }

  private final ManagingFS myManagingFS;
  private final MyFileWatcherNotificationSink myNotificationSink;
  private final AtomicBoolean myFailureShown = new AtomicBoolean(false);
  private final ExecutorService myFileWatcherExecutor = executor();
  private final AtomicReference<Future<?>> myLastTask = new AtomicReference<>(null);

  private volatile CanonicalPathMap myPathMap = CanonicalPathMap.empty();
  private volatile List<Collection<String>> myManualWatchRoots = Collections.emptyList();

  FileWatcher(@NotNull ManagingFS managingFS, @NotNull Runnable postInitCallback) {
    myManagingFS = managingFS;
    myNotificationSink = new MyFileWatcherNotificationSink();

    myFileWatcherExecutor.execute(() -> {
      PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> {
        watcher.initialize(myManagingFS, myNotificationSink);
      });
      if (isOperational()) {
        postInitCallback.run();
      }
    });
  }

  public void dispose() {
    myFileWatcherExecutor.shutdown();

    Future<?> lastTask = myLastTask.get();
    if (lastTask != null) {
      lastTask.cancel(false);
    }

    try {
      myFileWatcherExecutor.awaitTermination(1, TimeUnit.HOURS);
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }

    PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> {
      watcher.dispose();
    });
  }

  public boolean isOperational() {
    for (PluggableFileWatcher watcher : PluggableFileWatcher.EP_NAME.getIterable()) {
      if (watcher.isOperational()) return true;
    }
    return false;
  }

  public boolean isSettingRoots() {
    Future<?> lastTask = myLastTask.get();  // a new task may come after the read, but this seem to be an acceptable race
    if (lastTask != null && !lastTask.isDone()) {
      return true;
    }
    for (PluggableFileWatcher watcher : PluggableFileWatcher.EP_NAME.getIterable()) {
      if (watcher.isSettingRoots()) return true;
    }
    return false;
  }

  @NotNull DirtyPaths getDirtyPaths() {
    return myNotificationSink.getDirtyPaths();
  }

  public @NotNull Collection<@NotNull String> getManualWatchRoots() {
    List<Collection<String>> manualWatchRoots = myManualWatchRoots;

    Set<String> result = null;
    for (Collection<String> roots : manualWatchRoots) {
      if (result == null) {
        result = new HashSet<>(roots);
      }
      else {
        result.retainAll(roots);
      }
    }

    return result != null ? result : Collections.emptyList();
  }

  void setWatchRoots(@NotNull Supplier<CanonicalPathMap> pathMapSupplier) {
    Future<?> prevTask = myLastTask.getAndSet(myFileWatcherExecutor.submit(() -> {
      try {
        CanonicalPathMap pathMap = pathMapSupplier.get();
        if (pathMap == null) return;
        myPathMap = pathMap;
        myManualWatchRoots = ContainerUtil.createLockFreeCopyOnWriteList();

        Pair<List<String>, List<String>> roots = pathMap.getCanonicalWatchRoots();
        PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> {
          watcher.setWatchRoots(roots.first, roots.second);
        });
      }
      catch (RuntimeException | Error e) {
        LOG.error(e);
      }
    }));
    if (prevTask != null) {
      prevTask.cancel(false);
    }
  }

  public void notifyOnFailure(@NotNull String cause, @Nullable NotificationListener listener) {
    LOG.warn(cause);

    if (myFailureShown.compareAndSet(false, true)) {
      NotificationGroup group = NOTIFICATION_GROUP.getValue();
      String title = ApplicationBundle.message("watcher.slow.sync");
      ApplicationManager.getApplication().invokeLater(
        () -> Notifications.Bus.notify(group.createNotification(title, cause, NotificationType.WARNING, listener)),
        ModalityState.NON_MODAL);
    }
  }

  boolean belongsToWatchRoots(@NotNull String reportedPath, boolean isFile) {
    return myPathMap.belongsToWatchRoots(reportedPath, isFile);
  }

  @NotNull Collection<@NotNull String> mapToAllSymlinks(@NotNull String reportedPath) {
    Collection<String> result = myPathMap.mapToOriginalWatchRoots(reportedPath, true);
    if (!result.isEmpty()) {
      result.remove(reportedPath);
    }
    return result;
  }

  private final class MyFileWatcherNotificationSink implements FileWatcherNotificationSink {
    private final Object myLock = new Object();
    private DirtyPaths myDirtyPaths = new DirtyPaths();

    @NotNull DirtyPaths getDirtyPaths() {
      DirtyPaths dirtyPaths = DirtyPaths.EMPTY;

      synchronized (myLock) {
        if (!myDirtyPaths.isEmpty()) {
          dirtyPaths = myDirtyPaths;
          myDirtyPaths = new DirtyPaths();
        }
      }

      PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> {
        watcher.resetChangedPaths();
      });

      return dirtyPaths;
    }

    @Override
    public void notifyManualWatchRoots(@NotNull Collection<String> roots) {
      myManualWatchRoots.add(roots.isEmpty() ? Collections.emptySet() : new HashSet<>(roots));
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
      Collection<String> paths = myPathMap.mapToOriginalWatchRoots(path, true);
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
      Collection<String> paths = myPathMap.mapToOriginalWatchRoots(path, true);
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
      Collection<String> paths = myPathMap.mapToOriginalWatchRoots(path, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          myDirtyPaths.dirtyDirectories.addAll(paths);
        }
      }
      notifyOnEvent(path);
    }

    @Override
    public void notifyDirtyPathRecursive(@NotNull String path) {
      Collection<String> paths = myPathMap.mapToOriginalWatchRoots(path, false);
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

  private volatile Consumer<? super String> myTestNotifier;

  private void notifyOnEvent(String path) {
    Consumer<? super String> notifier = myTestNotifier;
    if (notifier != null) notifier.accept(path);
  }

  @TestOnly
  public void startup(@Nullable Consumer<? super String> notifier) throws Exception {
    myTestNotifier = notifier;
    myFileWatcherExecutor.submit(() -> {
      for (PluggableFileWatcher watcher : PluggableFileWatcher.EP_NAME.getIterable()) {
        watcher.startup();
      }
      return null;
    }).get();
  }

  @TestOnly
  public void shutdown() throws Exception {
    myFileWatcherExecutor.submit(() -> {
      for (PluggableFileWatcher watcher : PluggableFileWatcher.EP_NAME.getIterable()) {
        watcher.shutdown();
      }
      myTestNotifier = null;
      return null;
    }).get();
  }
  //</editor-fold>
}