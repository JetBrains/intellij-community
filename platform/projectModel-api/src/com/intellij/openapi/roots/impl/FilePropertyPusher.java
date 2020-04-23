// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DeprecatedMethodException;
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
 * <br/><br/>
 * <b>Note:</b> Don't use plugin-specific classes as pushed properties, consider using <code>String</code> instead.
 * Otherwise, the plugin becomes not dynamic because its classes will be hard-referenced as <code>VirtualFile</code>'s user data.
 * For example, instead of pushing <code>SomeLanguageDialect</code> instances, push <code>someLanguageDialect.getName()</code> and
 * restore <code>SomeLanguageDialect</code> by name where needed.
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

  default boolean acceptsFile(@NotNull VirtualFile file) {
    DeprecatedMethodException.report("Please override FilePropertyPusher#acceptsFile(VirtualFile, Project)");
    return false;
  }

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
