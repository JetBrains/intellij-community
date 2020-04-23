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
import com.intellij.util.DeprecatedMethodException;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Represents a non-linear operation which is executed before indexing process
 * is started {@link PushedFilePropertiesUpdater#pushAllPropertiesNow()}.
 * <br />
 * During this process any pusher is allowed to set some properties to any of files being indexed.
 * <br />
 * Most frequently property represents some kind of "language level"
 * which is in most cases required to determine the algorithm of stub and other indexes building.
 * <br />
 * After property was pushed it can be retrieved any time using {@link FilePropertyPusher#getFileDataKey()}.
 * <br/>
 * The computation of the value (simplified) for a given {@link VirtualFile} works as follows:
 * <ul>
 * <li> the {@link #getImmediateValue(Project, VirtualFile)} is executed,
 *      a non-null result is returned</li>
 * <li> the {@link #getImmediateValue(Module)} is executed for the module of a file,
 *      a non-null result is retuned</li>
 * <li> iterate all parent file that is in content root of the file
 *      and try to return first non-null
 *      result of the {@link #getImmediateValue(Project, VirtualFile)} call
 * </li>
 * <li> try calling the {@link #getImmediateValue(Project, VirtualFile)} with {@code null} as {@link VirtualFile}
 *      and return a non-null value</li>
 * <li> fallback to {@link #getDefaultValue()} and return it</li>
 * </ul>
 * Once the value is computed, it calls the {@link #persistAttribute(Project, VirtualFile, Object)}
 * to test if the value is changed, if so, the implementation is responsible to notify back
 * <br/>
 * This API is intended to be used to prepare state for indexing, please do not use it for other purposes
 * <br/><br/>
 * <b>Note:</b> Don't use plugin-specific classes as pushed properties, consider using <code>String</code> instead.
 * Otherwise, the plugin becomes not dynamic because its classes will be hard-referenced as <code>VirtualFile</code>'s user data.
 * For example, instead of pushing <code>SomeLanguageDialect</code> instances, push <code>someLanguageDialect.getName()</code> and
 * restore <code>SomeLanguageDialect</code> by name where needed.
 */
public interface FilePropertyPusher<T> {
  ExtensionPointName<FilePropertyPusher<?>> EP_NAME = ExtensionPointName.create("com.intellij.filePropertyPusher");

  default void initExtra(@NotNull Project project, @NotNull MessageBus bus) { }

  default void afterRootsChanged(@NotNull Project project) {}

  /**
   * After property was pushed it can be retrieved any time using {@link FilePropertyPusher#getFileDataKey()}
   * from {@link VirtualFile#getUserData(Key)}.
   */
  @NotNull
  Key<T> getFileDataKey();

  boolean pushDirectoriesOnly();

  @NotNull
  T getDefaultValue();

  @Nullable
  T getImmediateValue(@NotNull Module module);

  @Nullable
  T getImmediateValue(@NotNull Project project, @Nullable VirtualFile file);

  default boolean acceptsFile(@NotNull VirtualFile file, @NotNull Project project) {
    return acceptsFile(file);
  }

  boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project);

  /**
   * This method is called to persist the computed Pusher value (of type T).
   * The implementation is supposed to call {@link PushedFilePropertiesUpdater#filePropertiesChanged}
   * if a change is detected to issue the {@para fileOrDir} re-index
   */
  void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T value) throws IOException;

  //<editor-fold desc="Deprecated APIs" defaultState="collapsed">

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

  /**
   * @deprecated not used anymore
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  interface Engine {
    void pushAll();
    void pushRecursively(@NotNull VirtualFile vile, @NotNull Project project);
  }

  /**
   * @deprecated Please override {@link FilePropertyPusher#acceptsFile(VirtualFile, Project)}
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  default boolean acceptsFile(@NotNull VirtualFile file) {
    DeprecatedMethodException.report("Please override FilePropertyPusher#acceptsFile(VirtualFile, Project)");
    return false;
  }

  //</editor-fold>
}
