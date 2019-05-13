/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Model of roots that should be used by clients to modify module roots.
 *
 * @author dsl
 * @see ModuleRootManager#getModifiableModel()
 */
public interface ModifiableRootModel extends ModuleRootModel {
  @NotNull
  Project getProject();

  /**
   * Adds the specified file or directory as a content root.
   *
   * @param root root of a content
   * @return new content entry
   */
  @NotNull
  ContentEntry addContentEntry(@NotNull VirtualFile root);

  /**
   * Adds the specified file or directory as a content root.
   *
   * @param url root of a content
   * @return new content entry
   */
  @NotNull
  ContentEntry addContentEntry(@NotNull String url);

  /**
   * Remove the specified content root.
   *
   * @param entry the content root to remove.
   */
  void removeContentEntry(@NotNull ContentEntry entry);

  /**
   * Appends an order entry to the classpath.
   *
   * @param orderEntry the order entry to add.
   */
  void addOrderEntry(@NotNull OrderEntry orderEntry);

  /**
   * Creates an entry for a given library and adds it to order
   *
   * @param library the library for which the entry is created.
   * @return newly created order entry for the library
   */
  @NotNull
  LibraryOrderEntry addLibraryEntry(@NotNull Library library);

  /**
   * Adds an entry for invalid library.
   */
  @NotNull
  LibraryOrderEntry addInvalidLibrary(@NotNull @NonNls String name, @NotNull String level);

  @NotNull
  ModuleOrderEntry addModuleOrderEntry(@NotNull Module module);

  @NotNull
  ModuleOrderEntry addInvalidModuleEntry(@NotNull String name);

  @Nullable
  ModuleOrderEntry findModuleOrderEntry(@NotNull Module module);

  @Nullable
  LibraryOrderEntry findLibraryOrderEntry(@NotNull Library library);

  /**
   * Removes order entry from an order.
   */
  void removeOrderEntry(@NotNull OrderEntry orderEntry);

  void rearrangeOrderEntries(@NotNull OrderEntry[] newOrder);

  void clear();

  /**
   * Commits changes to a <code>{@link ModuleRootManager}</code>.
   * Should be invoked in a write action. After <code>commit()<code>, the model
   * becomes read-only.
   *
   * Use of ModuleRootModificationUtil.updateModel() is recommended.
   */
  void commit();

  /**
   * Must be invoked for uncommitted models that are no longer needed.
   *
   * Use of ModuleRootModificationUtil.updateModel() is recommended.
   */
  void dispose();

  /**
   * Returns library table with module libraries.<br>
   * <b>Note:</b> returned library table does not support listeners. Also one shouldn't invoke 'commit()' or 'dispose()' methods on it,
   * it is automatically committed or disposed along with this {@link ModifiableRootModel} instance.
   *
   * @return library table to be modified
   */
  @NotNull
  LibraryTable getModuleLibraryTable();

  /**
   * Sets JDK for this module to a specific value
   */
  void setSdk(@Nullable Sdk jdk);

  /**
   * Sets JDK name and type for this module.
   * To be used when JDK with this name and type does not exist (e.g. when importing module configuration).
   *
   * @param sdkName JDK name
   * @param sdkType JDK type
   */
  void setInvalidSdk(@NotNull String sdkName, String sdkType);

  /**
   * Makes this module inheriting JDK from its project
   */
  void inheritSdk();

  boolean isChanged();

  boolean isWritable();

  <T extends OrderEntry> void replaceEntryOfType(Class<T> entryClass, T entry);

  @Nullable
  String getSdkName();

  boolean isDisposed();
}
