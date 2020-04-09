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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Represents a non-linear operation which is executed before indexing process is started {@link PushedFilePropertiesUpdater#pushAllPropertiesNow()}.
 * During this process any pusher is allowed to set some properties to any of files being indexed.
 * Most frequently property represents some kind of "language level"
 * which is in most cases required to determine the algorithm of stub and other indexes building.
 * After property was pushed it can be retrieved any time using {@link FilePropertyPusher#getFileDataKey()}.
 */
public interface FilePropertyPusher<T> {
  ExtensionPointName<FilePropertyPusher<?>> EP_NAME = ExtensionPointName.create("com.intellij.filePropertyPusher");

  default void initExtra(@NotNull Project project, @NotNull MessageBus bus) { }

  /**
   * @deprecated
   * use {@link FilePropertyPusher#initExtra(Project, MessageBus)} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  @SuppressWarnings("unused")
  default void initExtra(@NotNull Project project, @NotNull MessageBus bus, @NotNull Engine languageLevelUpdater) {
    initExtra(project, bus);
  }

  @NotNull
  Key<T> getFileDataKey();
  boolean pushDirectoriesOnly();

  @NotNull
  T getDefaultValue();

  @Nullable
  T getImmediateValue(@NotNull Project project, @Nullable VirtualFile file);

  @Nullable
  T getImmediateValue(@NotNull Module module);

  default boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    return acceptsFile(file);
  }

  boolean acceptsFile(@NotNull VirtualFile file);
  boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project);

  void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T value) throws IOException;

  /**
   * @deprecated not used anymore
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  interface Engine {
    void pushAll();
    void pushRecursively(@NotNull VirtualFile vile, @NotNull Project project);
  }

  default void afterRootsChanged(@NotNull Project project) {}
}
