/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author max
 */
public interface ApplicationEx extends Application {
  /**
   * Loads the application configuration from the specified path
   *
   * @param optionsPath Path to /config folder
   * @throws IOException
   * @throws InvalidDataException
   */
  void load(String optionsPath) throws IOException, InvalidDataException;

  boolean isInternal();

  String getName();

  boolean holdsReadLock();

  void assertReadAccessToDocumentsAllowed();

  void doNotSave();

  boolean isDoNotSave();

  //force exit
  void exit(boolean force);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(final Runnable process,
                                              String progressTitle,
                                              boolean canBeCanceled,
                                              Project project);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(final Runnable process,
                                              String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project, JComponent parentComponent);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(final Runnable process,
                                              String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project, JComponent parentComponent, final String cancelText);

  boolean isInModalProgressThread();

  /**
   * Whenever
   * - one thread acquired read action,
   * - launches another thread which is supposed to tun under read action too,
   * - and waits for that thread completion
   * it's a straight road to deadlock (because if anyone tries to start write action in the unlucky moment,
   * it will block waiting for the read action completion,
   * and the second readaction will block according to ReentrantWriterPreferenceReadWriteLock policy)
   *
   * So this is the only right way to wait multiple threads with read action for completion.
   */
  <T> List<Future<T>> invokeAllUnderReadAction(@NotNull Collection<Callable<T>> tasks, ExecutorService executorService) throws Throwable;
  
  void assertIsDispatchThread(@Nullable JComponent component);

  void assertTimeConsuming();

  void runEdtSafeAction(@NotNull Runnable runnable);

  /**
   * Grab the lock and run the action, in a nonblocking fashion
   * @return true if action was run while holding the lock, false if was unable to get the lock and action was not run
   */
  boolean tryRunReadAction(@NotNull Runnable action);
}
