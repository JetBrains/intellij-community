// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * A common executor for non-urgent tasks, which are expected to be fast (preferably not more than 10 seconds) most of the time.
 * Used to avoid spawning a lot of threads by different subsystems all reacting to the same event,
 * when all they have to do is several short PSI/index queries in reaction to a file or project model change,
 * or on project opening. If you're using {@link com.intellij.openapi.application.ReadAction#nonBlocking},
 * you might consider this executor as backend. This executor is bounded, so please don't perform long-running
 * or potentially blocking operations here.
 * <p></p>
 *
 * Not to be used:
 * <ul>
 *   <li>For activities that can take significant time, e.g. project-wide Find Usages, or a Web query or slow I/O</li>
 *   <li>For background processes started by user actions, where people would wait for results, staring at the screen impatiently.</li>
 * </ul>
 */
public final class NonUrgentExecutor implements Executor {
  private static final NonUrgentExecutor ourInstance = new NonUrgentExecutor();
  private final Executor myBackend = AppExecutorUtil.createBoundedApplicationPoolExecutor("NonUrgentExecutor", 2, false);

  private NonUrgentExecutor() {
  }

  @Override
  public void execute(@NotNull Runnable command) {
    myBackend.execute(command);
  }

  public static @NotNull NonUrgentExecutor getInstance() {
    return ourInstance;
  }
}
