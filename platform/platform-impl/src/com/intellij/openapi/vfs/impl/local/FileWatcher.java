/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.local;

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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

  public static class DirtyPaths {
    public final Set<String> dirtyPaths = ContainerUtil.newTroveSet();
    public final Set<String> dirtyPathsRecursive = ContainerUtil.newTroveSet();
    public final Set<String> dirtyDirectories = ContainerUtil.newTroveSet();

    public static final DirtyPaths EMPTY = new DirtyPaths();

    public boolean isEmpty() {
      return dirtyPaths.isEmpty() && dirtyPathsRecursive.isEmpty() && dirtyDirectories.isEmpty();
    }
  }

  private final MyFileWatcherNotificationSink myNotificationSink;
  private final PluggableFileWatcher[] myWatchers;
  private final AtomicBoolean myFailureShown = new AtomicBoolean(false);

  private volatile CanonicalPathMap myPathMap = new CanonicalPathMap();
  private volatile List<Collection<String>> myManualWatchRoots = Collections.emptyList();

  FileWatcher(@NotNull ManagingFS managingFS) {
    myNotificationSink = new MyFileWatcherNotificationSink();
    myWatchers = PluggableFileWatcher.EP_NAME.getExtensions();
    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.initialize(managingFS, myNotificationSink);
    }
  }

  public void dispose() {
    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.dispose();
    }
  }

  public boolean isOperational() {
    for (PluggableFileWatcher watcher : myWatchers) {
      if (!watcher.isOperational()) return false;
    }
    return true;
  }

  public boolean isSettingRoots() {
    for (PluggableFileWatcher watcher : myWatchers) {
      if (watcher.isSettingRoots()) return true;
    }
    return false;
  }

  @NotNull
  public DirtyPaths getDirtyPaths() {
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

    return result != null ? result : Collections.<String>emptyList();
  }

  /**
   * Clients should take care of not calling this method in parallel.
   */
  public void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat) {
    CanonicalPathMap pathMap = new CanonicalPathMap(recursive, flat);

    myPathMap = pathMap;
    myManualWatchRoots = ContainerUtil.createLockFreeCopyOnWriteList();

    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.setWatchRoots(pathMap.getCanonicalRecursiveWatchRoots(), pathMap.getCanonicalFlatWatchRoots());
    }
  }

  public boolean isWatched(@NotNull VirtualFile file) {
    // At the moment, "watched" means "monitored by at least one operational watcher".
    // The following condition matches the above statement only for a single watcher, but this should work for a moment.
    // todo[r.sh] reconsider usages of isWatched() and getManualWatchRoots() in LFS and refresh session
    return isOperational() && !myPathMap.getWatchedPaths(file.getPath(), true, true).isEmpty();
  }

  public void notifyOnFailure(final String cause, @Nullable final NotificationListener listener) {
    LOG.warn(cause);

    if (myFailureShown.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          String title = ApplicationBundle.message("watcher.slow.sync");
          Notifications.Bus.notify(NOTIFICATION_GROUP.getValue().createNotification(title, cause, NotificationType.WARNING, listener));
        }
      }, ModalityState.NON_MODAL);
    }
  }

  private class MyFileWatcherNotificationSink implements FileWatcherNotificationSink {
    private final Object myLock = new Object();
    private DirtyPaths myDirtyPaths = new DirtyPaths();

    private DirtyPaths getDirtyPaths() {
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
      if (!roots.isEmpty()) {
        myManualWatchRoots.add(ContainerUtil.newHashSet(roots));
      }
      notifyOnAnyEvent();
    }

    @Override
    public void notifyMapping(@NotNull Collection<Pair<String, String>> mapping) {
      if (!mapping.isEmpty()) {
        myPathMap.addMapping(mapping);
      }
      notifyOnAnyEvent();
    }

    @Override
    public void notifyDirtyPath(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, true, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          for (String eachPath : paths) {
            doAddDirtyPath(eachPath);
          }
        }
      }
      notifyOnAnyEvent();
    }

    private void doAddDirtyPath(String path) {
      if (!myDirtyPaths.dirtyPathsRecursive.contains(path)) {
        myDirtyPaths.dirtyPaths.add(path);
      }
    }

    private void doAddDirtyPathRecursive(String path) {
      myDirtyPaths.dirtyPaths.remove(path);
      myDirtyPaths.dirtyPathsRecursive.add(path);
    }

    @Override
    public void notifyPathCreatedOrDeleted(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, true, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          for (String p : paths) {
            doAddDirtyPathRecursive(p);
            String parentPath = new File(p).getParent();
            if (parentPath != null) {
              doAddDirtyPath(parentPath);
            }
          }
        }
      }
      notifyOnAnyEvent();
    }

    @Override
    public void notifyDirtyDirectory(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, false, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          myDirtyPaths.dirtyDirectories.addAll(paths);
        }
      }
      notifyOnAnyEvent();
    }

    @Override
    public void notifyDirtyPathRecursive(@NotNull String path) {
      Collection<String> paths = myPathMap.getWatchedPaths(path, false, false);
      if (!paths.isEmpty()) {
        synchronized (myLock) {
          for (String each : paths) {
            doAddDirtyPathRecursive(each);
          }
        }
      }
      notifyOnAnyEvent();
    }

    @Override
    public void notifyUserOnFailure(@NotNull String cause, @Nullable NotificationListener listener) {
      notifyOnFailure(cause, listener);
    }
  }

  /* test data and methods */

  private volatile Runnable myTestNotifier = null;

  private void notifyOnAnyEvent() {
    Runnable notifier = myTestNotifier;
    if (notifier != null) {
      notifier.run();
    }
  }

  @TestOnly
  public void shutdown() throws InterruptedException {
    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.shutdown();
    }
    myTestNotifier = null;
  }

  @TestOnly
  public void startup(@Nullable Runnable notifier) throws IOException {
    myTestNotifier = notifier;
    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.startup();
    }
  }
}