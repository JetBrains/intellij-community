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
package org.jetbrains.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface DebugEventListener extends EventListener {
  /**
   * Reports the virtual machine has suspended (on hitting
   * breakpoints or a step end). The {@code context} can be used to access the
   * current backtrace.
   */
  void suspended(@NotNull SuspendContext<?> context);

  /**
   * Reports the virtual machine has resumed. This can happen
   * asynchronously, due to a user action in the browser (without explicitly resuming the VM through
   */
  void resumed();

  /**
   * Reports the debug connection has terminated and {@link Vm} has stopped operating.
   * This event is not reported if connection was closed explicitly on our side
   */
  void disconnected();

  /**
   * Reports that a new script has been loaded.
   */
  void scriptAdded(@NotNull Script script, @Nullable String sourceMapUrl);

  /**
   * Reports that the script has been collected and is no longer used in VM.
   */
  void scriptRemoved(@NotNull Script script);

  void scriptsCleared();

  /**
   * Reports that script source has been altered in remote VM.
   */
  void scriptContentChanged(@NotNull Script newScript);

  /**
   * Reports a navigation event on the target.
   *
   * @param newUrl the new URL of the debugged target
   */
  void navigated(String newUrl);

  void errorOccurred(@NotNull String errorMessage);
}