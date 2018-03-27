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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LineStatusTrackerManagerI {
  @Nullable
  LineStatusTracker<?> getLineStatusTracker(Document document);

  @Nullable
  LineStatusTracker<?> getLineStatusTracker(@NotNull VirtualFile file);

  @CalledInAwt
  void requestTrackerFor(@NotNull Document document, @NotNull Object requester);

  @CalledInAwt
  void releaseTrackerFor(@NotNull Document document, @NotNull Object requester);


  boolean arePartialChangelistsEnabled(@NotNull VirtualFile virtualFile);

  void invokeAfterUpdate(@NotNull Runnable task);
}
