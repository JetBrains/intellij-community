// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unless stated otherwise, all paths are {@link SystemDependent @SystemDependent}.
 */
public final class FileWatcher implements AppLifecycleListener {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  final static class DirtyPaths {
    final Set<String> dirtyPaths = new HashSet<>();
    final Set<String> dirtyPathsRecursive = new HashSet<>();
    final Set<String> dirtyDirectories = new HashSet<>();

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

  private final ManagingFS myManagingFS;
  private final MyFileWatcherNotificationSink myNotificationSink;
  private final ExecutorService myFileWatcherExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("File Watcher", 1);
  private final AtomicReference<Future<?>> myLastTask = new AtomicReference<>(null);

  private volatile boolean myShuttingDown = false;
  private volatile CanonicalPathMap myPathMap = CanonicalPathMap.empty();
  private volatile Map<Object, Set<String>> myManualWatchRoots = Collections.emptyMap();

  FileWatcher(@NotNull ManagingFS managingFS, @NotNull Runnable postInitCallback) {
    myManagingFS = managingFS;
    myNotificationSink = new MyFileWatcherNotificationSink();

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, this);

    myFileWatcherExecutor.execute(() -> {
      PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> watcher.initialize(myManagingFS, myNotificationSink));
      if (isOperational()) {
        postInitCallback.run();
      }
    });
  }

  @Override
  public void appWillBeClosed(boolean isRestart) {
    myShuttingDown = true;
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

    PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> watcher.dispose());
  }

  public boolean isOperational() {
    for (PluggableFileWatcher watcher : PluggableFileWatcher.EP_NAME.getIterable()) {
      if (watcher.isOperational()) return true;
    }
    return false;
  }

  public boolean isSettingRoots() {
    Future<?> lastTask = myLastTask.get();  // a new task may come after the read, but this seems to be an acceptable race
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
    Set<String> result = null;
    for (Set<String> rootSet : myManualWatchRoots.values()) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (rootSet) {
        if (result == null) {
          result = new HashSet<>(rootSet);
        }
        else {
          result.retainAll(rootSet);
        }
      }
    }
    return result != null ? result : Collections.emptyList();
  }

  void setWatchRoots(@NotNull Supplier<CanonicalPathMap> pathMapSupplier) {
    Future<?> prevTask = myLastTask.getAndSet(myFileWatcherExecutor.submit(() -> {
      try {
        var pathMap = myShuttingDown ? CanonicalPathMap.empty() : pathMapSupplier.get();
        if (pathMap == null) return;
        myPathMap = pathMap;
        myManualWatchRoots = new ConcurrentHashMap<>();

        Pair<List<String>, List<String>> roots = pathMap.getCanonicalWatchRoots();
        PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> {
          if (watcher.isOperational()) {
            watcher.setWatchRoots(roots.first, roots.second, myShuttingDown);
          }
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

  public void notifyOnFailure(@NotNull @NlsContexts.NotificationContent String cause, @Nullable NotificationListener listener) {
    LOG.warn(cause);

    String title = IdeCoreBundle.message("watcher.slow.sync");
    Notification notification = new Notification("File Watcher Messages", title, cause, NotificationType.WARNING)
      .setSuggestionType(true);
    if (listener != null) {
      //noinspection deprecation
      notification.setListener(listener);
    }
    notification.notify(null);
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

      PluggableFileWatcher.EP_NAME.forEachExtensionSafe(watcher -> watcher.resetChangedPaths());

      return dirtyPaths;
    }

    @Override
    public void notifyManualWatchRoots(@NotNull PluggableFileWatcher watcher, @NotNull Collection<String> roots) {
      registerManualWatchRoots(watcher, roots);
    }

    private void registerManualWatchRoots(Object key, Collection<String> roots) {
      Set<String> rootSet = myManualWatchRoots.computeIfAbsent(key, k -> new HashSet<>());
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (rootSet) { rootSet.addAll(roots); }
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
    public void notifyUserOnFailure(@NotNull @NlsContexts.NotificationContent String cause, @Nullable NotificationListener listener) {
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
