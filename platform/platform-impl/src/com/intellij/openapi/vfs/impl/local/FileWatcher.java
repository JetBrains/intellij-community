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

import com.google.common.collect.Lists;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.FileWatcherResponsePath;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import com.intellij.openapi.vfs.local.PluggableFileWatcher;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.newArrayList;

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

  private final Object myLock = new Object();
  private final PluggableFileWatcher[] myWatchers;
  private CanonicalFileMapper myCanonicalFileMapper;
  @NotNull private List<String> recursiveCanonicalWatchRoots = Lists.newArrayList();
  @NotNull private List<String> flatCanonicalWatchRoots = Lists.newArrayList();

  public static class DirtyPaths {
    public final List<String> dirtyPaths = newArrayList();
    public final List<String> dirtyPathsRecursive = newArrayList();
    public final List<String> dirtyDirectories = newArrayList();

    public static final DirtyPaths EMPTY = new DirtyPaths();

    public boolean isEmpty() {
      return dirtyPaths.isEmpty() && dirtyPathsRecursive.isEmpty() && dirtyDirectories.isEmpty();
    }
  }

  private DirtyPaths myDirtyPaths = new DirtyPaths();
  private final AtomicBoolean myFailureShownToTheUser = new AtomicBoolean(false);

  FileWatcher(@NotNull ManagingFS managingFS) {
    MyFileWatcherNotificationSink notificationSink = new MyFileWatcherNotificationSink();
    myWatchers = PluggableFileWatcher.EP_NAME.getExtensions();
    // Load in an empty map
    synchronized (myLock) {
      myCanonicalFileMapper = CanonicalFileMapper.create();
    }
    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.initialize(managingFS, notificationSink);
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
    synchronized (myLock) {
      if (!myDirtyPaths.isEmpty()) {
        DirtyPaths dirtyPaths = myDirtyPaths;
        myDirtyPaths = new DirtyPaths();
        for (PluggableFileWatcher watcher : myWatchers) {
          watcher.resetChangedPaths();
        }
        return dirtyPaths;
      }
      else {
        return DirtyPaths.EMPTY;
      }
    }
  }

  @NotNull
  public List<String> getManualWatchRoots() {
    if (myWatchers.length == 1) {
      return myWatchers[0].getManualWatchRoots();
    }
    HashSet<String> result = null;
    for (PluggableFileWatcher watcher : myWatchers) {
      List<String> roots = watcher.getManualWatchRoots();
      if (result == null) {
        result = new HashSet<String>(roots);
      } else {
        result.retainAll(roots);
      }
    }
    if (result == null) return emptyList();
    synchronized (myLock) {
      return myCanonicalFileMapper.getMapping(FileWatcherResponsePath.toFileWatcherResponsePaths(result, false /* isParentOfDirtyPath */));
    }
  }

  public void setWatchRoots(@NotNull List<String> recursive, @NotNull List<String> flat) {
    synchronized (myLock) {
      // Clear out our old canonical path -> symbolic link map
      myCanonicalFileMapper = CanonicalFileMapper.create();

      recursiveCanonicalWatchRoots = myCanonicalFileMapper.addMappings(recursive, CanonicalFileMapper.MappingType.RECURSIVE);
      flatCanonicalWatchRoots = myCanonicalFileMapper.addMappings(flat, CanonicalFileMapper.MappingType.FLAT);
    }
    for (PluggableFileWatcher watcher : myWatchers) {
      watcher.setWatchRoots(recursiveCanonicalWatchRoots, flatCanonicalWatchRoots);
    }
  }

  /**
   * @return true if the file is under a watch root and at least one {@link PluggableFileWatcher} is not ignoring it.
   */
  public boolean isWatched(@NotNull VirtualFile file) {
    VirtualFile canonicalFile = file.getCanonicalFile();
    if (canonicalFile == null) {
      return false;
    }
    if (!isUnderWatchRoot(file)) {
      return false;
    }

    for (PluggableFileWatcher watcher : myWatchers) {
      if (!watcher.isIgnored(canonicalFile)) {
        return true;
      }
    }
    return false;
  }

  private boolean isUnderWatchRoot(@NotNull VirtualFile canonicalFile) {
    List<String> mapping = myCanonicalFileMapper.getMapping(new FileWatcherResponsePath(canonicalFile.getPath(),
                                                                                        false /* isParentOfDirtyPath */));
    return !mapping.isEmpty();
  }

  public void notifyOnFailure(final String cause, @Nullable final NotificationListener listener) {
    LOG.warn(cause);

    if (myFailureShownToTheUser.compareAndSet(false, true)) ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        String title = ApplicationBundle.message("watcher.slow.sync");
        Notifications.Bus.notify(NOTIFICATION_GROUP.getValue().createNotification(title, cause, NotificationType.WARNING, listener));
      }
    }, ModalityState.NON_MODAL);
  }

  private class MyFileWatcherNotificationSink implements FileWatcherNotificationSink {
    @Override
    public void notifyDirtyPaths(Collection<FileWatcherResponsePath> paths) {
      synchronized (myLock) {
        Collection<String> nonCanonicalPaths = myCanonicalFileMapper.getMapping(paths);
        myDirtyPaths.dirtyPaths.addAll(nonCanonicalPaths);
      }
    }

    @Override
    public void notifyDirtyDirectories(Collection<FileWatcherResponsePath> paths) {
      synchronized (myLock) {
        Collection<String> nonCanonicalPaths = myCanonicalFileMapper.getMapping(paths);
        myDirtyPaths.dirtyDirectories.addAll(nonCanonicalPaths);
      }
    }

    @Override
    public void notifyPathsRecursive(Collection<FileWatcherResponsePath> paths) {
      synchronized (myLock) {
        Collection<String> nonCanonicalPaths = myCanonicalFileMapper.getMapping(paths);
        myDirtyPaths.dirtyPathsRecursive.addAll(nonCanonicalPaths);
      }
    }

    @Override
    public void notifyPathsCreatedOrDeleted(Collection<FileWatcherResponsePath> paths) {
      synchronized (myLock) {
        Collection<String> nonCanonicalPaths = myCanonicalFileMapper.getMapping(paths);
        for (String p : nonCanonicalPaths) {
          myDirtyPaths.dirtyPathsRecursive.add(p);
          String parentPath = new File(p).getParent();
          if (parentPath != null) {
            myDirtyPaths.dirtyPaths.add(parentPath);
          }
        }
      }
    }

    @Override
    public void notifyOnAnyEvent() {
      final Runnable notifier = myTestNotifier;
      if (notifier != null) {
        notifier.run();
      }
    }

    @Override
    public void notifyUserOnFailure(String cause, NotificationListener listener) {
      notifyOnFailure(cause, listener);
    }
  }

  /* test data and methods */
  private volatile Runnable myTestNotifier = null;

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
