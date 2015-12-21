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

public abstract class DebugEventAdapter implements DebugEventListener {
  @Override
  public void suspended(@NotNull SuspendContext<?> context) {
  }

  @Override
  public void resumed() {
  }

  @Override
  public void disconnected() {
  }

  @Override
  public void scriptAdded(@NotNull Script script, @Nullable String sourceMapUrl) {
  }

  @Override
  public void scriptRemoved(@NotNull Script script) {
  }

  @Override
  public void scriptContentChanged(@NotNull Script newScript) {
  }

  @Override
  public void scriptsCleared() {
  }

  @Override
  public void navigated(String newUrl) {
  }

  @Override
  public void errorOccurred(@NotNull String errorMessage) {
  }
}
