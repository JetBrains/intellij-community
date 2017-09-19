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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.concurrency.AppExecutorUtil.createBoundedScheduledExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This class is intended to refresh virtual files periodically.
 *
 * @author Sergey.Malenkov
 */
public class FileRefresher implements Disposable {
  private static final Logger LOG = Logger.getInstance(FileRefresher.class);
  private final ScheduledExecutorService executor = createBoundedScheduledExecutorService("FileRefresher", 1);
  private final boolean recursive;
  private final long delay;
  private final ArrayList<Object> watchers = new ArrayList<>();
  private final ArrayList<VirtualFile> files = new ArrayList<>();
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private final AtomicBoolean launched = new AtomicBoolean();
  private final AtomicBoolean paused = new AtomicBoolean();
  private final AtomicBoolean disposed = new AtomicBoolean();
  private RefreshSession session; // synchronized by files

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

  /**
   * @return {@code true} if files should be considered as roots
   */
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
   * Registers the specified file to watch and to refresh.
   *
   * @param file a file to watch and to refresh
   */
  public final void register(VirtualFile file) {
    if (file != null && !disposed.get()) {
      LOG.debug("add file to watch recursive=", recursive, ": ", file);
      Object watcher = watch(file, recursive);
      if (watcher != null) {
        synchronized (watchers) {
          watchers.add(watcher);
        }
      }
      synchronized (files) {
        files.add(file);
      }
      if (!paused.get()) schedule();
    }
  }

  /**
   * Pauses files refreshing.
   */
  public final void pause() {
    LOG.debug("pause");
    paused.set(true);
  }

  /**
   * Starts files refreshing immediately.
   * If files are refreshing now, it will be restarted when finished.
   */
  public final void start() {
    LOG.debug("start");
    paused.set(false);
    launch();
  }

  private void schedule() {
    LOG.debug("schedule");
    if (disposed.get() || scheduled.getAndSet(true)) return;
    synchronized (files) {
      if (session != null || files.isEmpty()) return;
    }
    LOG.debug("scheduled for ", delay, " seconds");
    executor.schedule(this::launch, delay, SECONDS);
  }

  private void launch() {
    LOG.debug("launch");
    if (disposed.get() || launched.getAndSet(true)) return;
    RefreshSession session;
    synchronized (files) {
      if (this.session != null || files.isEmpty()) return;
      session = RefreshQueue.getInstance().createSession(true, recursive, this::finish);
      session.addAllFiles(files);
      this.session = session;
    }
    scheduled.set(false);
    launched.set(false);
    LOG.debug("launched at ", System.currentTimeMillis());
    session.launch();
  }

  private void finish() {
    LOG.debug("finished at ", System.currentTimeMillis());
    synchronized (files) {
      session = null;
    }
    if (launched.getAndSet(false)) {
      launch();
    }
    else if (scheduled.getAndSet(false) || !paused.get()) {
      schedule();
    }
  }

  @Override
  public void dispose() {
    LOG.debug("dispose");
    if (!disposed.getAndSet(true)) {
      synchronized (watchers) {
        watchers.forEach(this::unwatch);
        watchers.clear();
      }
      RefreshSession session;
      synchronized (files) {
        files.clear();
        session = this.session;
        this.session = null;
      }
      if (session != null) RefreshQueue.getInstance().cancelSession(session.getId());
    }
  }
}
