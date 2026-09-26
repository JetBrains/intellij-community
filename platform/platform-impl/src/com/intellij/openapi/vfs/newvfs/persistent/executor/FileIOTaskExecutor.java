// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.executor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Responsible for file IO task execution in VFS -- abstracts out synchronous and asynchronous IO under the same interface.
 * Files are identified by unique fileIds: fileIds are expected to have the same semantics as VFS fileIds, but not required to
 * be specifically fileId from VFS -- could be any unique integer ids.
 */
@ApiStatus.Internal
public interface FileIOTaskExecutor<T extends FileIOTaskExecutor.FileIOTask> extends AutoCloseable {
  /** @return true if executor has task(s) for fileId that are in queue or is currently executing (=in progress) */
  boolean hasUnfinishedTasksFor(int fileId);

  /**
   * @return the task for fileId that is currently in queue, or being executed (=in progress), if any,
   * or null, if there is no task for fileId currently in executor
   */
  @Nullable T unfinishedTaskOrNull(int fileId);

  /** @return true if there are unfinished tasks -- postponed, or started execution but not yet finished */
  boolean hasUnfinishedTasks();

  /**
   * Execute an IO task for the file.
   * <p>
   * The task could be executed immediately or postponed to be executed later -- depending on the implementation, and
   * conditions like configuration, requestor, size/number of already postponed writes, etc.
   *
   * @return true if the task is postponed to be executed later, false if the task has been executed immediately
   */
  boolean execute(@NotNull T task) throws Exception;

  /**
   * Execute postponed tasks (if any) in the current thread.
   * Does nothing if no tasks are postponed
   */
  void flush() throws Exception;

  @Override
  void close() throws Exception;


  interface FileIOTask {
    int fileId();

    void execute(boolean executedOnBackground) throws Exception;

    /**
     * @return true if the task _could_ be postponed and executed later, possibly on a background thread, false if the task
     * must be executed synchronously, in calling thread.
     * Even if postponed execution is 'allowed' -- implementation could still decide to run the task synchronously, in calling thread.
     */
    boolean isAsyncExecutionAllowed();
  }
}