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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Gregory.Shrago
 */
public interface FilePropertyPusher<T> {
  ExtensionPointName<FilePropertyPusher> EP_NAME = ExtensionPointName.create("com.intellij.filePropertyPusher");

  void initExtra(@NotNull Project project, @NotNull MessageBus bus, @NotNull Engine languageLevelUpdater);
  @NotNull
  Key<T> getFileDataKey();
  boolean pushDirectoriesOnly();

  @NotNull
  T getDefaultValue();

  @Nullable
  T getImmediateValue(@NotNull Project project, @Nullable VirtualFile file);

  @Nullable
  T getImmediateValue(@NotNull Module module);

  boolean acceptsFile(@NotNull VirtualFile file);
  boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project);

  void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T value) throws IOException;

  interface Engine {
    void pushAll();
    void pushRecursively(VirtualFile vile, Project project);
  }

  void afterRootsChanged(@NotNull Project project);
}
