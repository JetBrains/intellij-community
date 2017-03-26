/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.util.concurrency.AppExecutorUtil.createBoundedScheduledExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

/**
 * This class is intended to refresh virtual files periodically.
 *
 * @author Sergey.Malenkov
 */
public class FileRefresher implements Disposable {
  private final ScheduledExecutorService executor = createBoundedScheduledExecutorService("FileRefresher", 1);
  private final boolean recursive;
  private final long delay;
  private final AtomicReference<RefreshSession> session = new AtomicReference<>();
  private final AtomicReference<List<Object>> watchers = new AtomicReference<>();
  private volatile List<VirtualFile> files;
  private volatile boolean disposed;
  private volatile boolean paused;

  /**
   * @param recursive {@code true} if files should be considered as roots
   * @param delay     an amount of seconds before refreshing files
   * @throws IllegalArgumentException if the specified delay is not positive
   */
  public FileRefresher(boolean recursive, long delay) {
    if (delay <= 0) throw new IllegalArgumentException("delay");
    this.recursive = recursive;
    this.delay = delay;
  }

  public boolean isRecursive() {
    return recursive;
  }

  /**
   * Starts watching the specified file, which was added before.
   *
   * @param file      a file to watch
   * @param recursive {@code true} if a file should be considered as root
   * @return an object that allows to stop watching the specified file
   */
  protected Object watch(VirtualFile file, boolean recursive) {
    VirtualFileSystem fs = file.getFileSystem();
    if (fs instanceof LocalFileSystem) {
      return LocalFileSystem.getInstance().addRootToWatch(file.getPath(), recursive);
    }
    return null;
  }

  /**
   * Stops watching file, which was added before.
   *
   * @param watcher an object that allows to stop watching a file
   */
  protected void unwatch(Object watcher) {
    if (watcher instanceof LocalFileSystem.WatchRequest) {
      LocalFileSystem.getInstance().removeWatchedRoot((LocalFileSystem.WatchRequest)watcher);
    }
  }

  /**
   * Replaces current list of files to watch.
   * It stops watching files, which were added before.
   * Then it starts watching new files and schedules refreshing.
   *
   * @param files a list of files to watch
   */
  public final void setFiles(List<VirtualFile> files) {
    if (!disposed) {
      unwatch();
      if (files != null) {
        files = files.stream().filter(Objects::nonNull).collect(toList()); // create a copy of the specified list
        if (files.isEmpty()) files = null;
      }
      this.files = files;
      if (files != null) {
        this.watchers.set(files.stream().map(file -> watch(file, recursive)).filter(Objects::nonNull).collect(toList()));
        start();
      }
    }
  }

  /**
   * Pauses files refreshing.
   */
  public final void pause() {
    paused = true;
  }

  /**
   * Schedules files refreshing.
   */
  public final void start() {
    paused = false;
    if (!disposed) executor.schedule(this::launch, delay, SECONDS);
  }

  private void launch() {
    List<VirtualFile> files = this.files;
    if (!disposed && !paused && files != null) {
      RefreshSession session = RefreshQueue.getInstance().createSession(true, recursive, this::finish);
      if (this.session.compareAndSet(null, session)) {
        session.addAllFiles(files);
        session.launch();
      }
    }
  }

  private void finish() {
    session.set(null);
    if (!disposed && !paused) start();
  }

  private void unwatch() {
    List<Object> watchers = this.watchers.getAndSet(null);
    if (watchers != null) watchers.forEach(this::unwatch);
  }

  @Override
  public void dispose() {
    disposed = true;
    unwatch();
    RefreshSession session = this.session.getAndSet(null);
    if (session != null) RefreshQueue.getInstance().cancelSession(session.getId());
  }
}
