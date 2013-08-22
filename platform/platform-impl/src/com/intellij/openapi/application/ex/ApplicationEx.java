/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author max
 */
public interface ApplicationEx extends Application {
  String LOCATOR_FILE_NAME = ".home";

  /**
   * Loads the application configuration from the specified path
   *
   * @param optionsPath Path to /config folder
   * @throws IOException
   * @throws InvalidDataException
   */
  void load(String optionsPath) throws IOException, InvalidDataException;
  boolean isLoaded();

  @NotNull
  String getName();

  boolean holdsReadLock();

  void doNotSave();
  void doNotSave(boolean value);
  boolean isDoNotSave();

  //force exit
  void exit(boolean force);

  void restart(boolean force);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              Project project);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project,
                                              JComponent parentComponent);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project,
                                              JComponent parentComponent,
                                              final String cancelText);

  void assertIsDispatchThread(@Nullable JComponent component);

  void assertTimeConsuming();

  void runEdtSafeAction(@NotNull Runnable runnable);

  /**
   * Grab the lock and run the action, in a non-blocking fashion
   *
   * @return true if action was run while holding the lock, false if was unable to get the lock and action was not run
   */
  boolean tryRunReadAction(@NotNull Runnable action);
}
