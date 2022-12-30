/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Model of roots that should be used by clients to modify module roots.
 * <p/>
 * Invoke {@link #commit()} to persist changes, see also {@link ModuleRootModificationUtil}.
 *
 * @author dsl
 * @see ModuleRootManager#getModifiableModel()
 * @see ModuleRootModificationUtil
 */
@ApiStatus.NonExtendable
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
   * Also this method specifies an external source
   *
   * @param root root of a content
   * @return new content entry
   */
  ContentEntry addContentEntry(@NotNull VirtualFile root, @NotNull ProjectModelExternalSource externalSource);

  /**
   * Adds the specified file or directory as a content root.
   *
   * @param url root of a content
   * @return new content entry
   */
  @NotNull
  ContentEntry addContentEntry(@NotNull String url);

  /**
   * Adds the specified file or directory as a content root.
   * Also this method specifies an external source
   *
   * @param url root of a content
   * @return new content entry
   */
  ContentEntry addContentEntry(@NotNull String url, @NotNull ProjectModelExternalSource externalSource);

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
   * Creates an entry for a given library and adds it to order.
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

  /**
   * Adds dependencies on several {@code libraries} and sets the specified {@code scope} and {@code exported} flag for them. This works
   * faster than adding these dependencies one-by-one via {@link #addLibraryEntry}.
   */
  @ApiStatus.Experimental
  void addLibraryEntries(@NotNull List<Library> libraries, @NotNull DependencyScope scope, boolean exported);

  @NotNull
  ModuleOrderEntry addModuleOrderEntry(@NotNull Module module);

  @NotNull
  ModuleOrderEntry addInvalidModuleEntry(@NotNull String name);

  /**
   * Adds dependencies on several {@code modules} and sets the specified {@code scope} and {@code exported} flag for them. This works
   * faster than adding these dependencies one-by-one via {@link #addModuleOrderEntry}.
   */
  @ApiStatus.Experimental
  void addModuleEntries(@NotNull List<Module> modules, @NotNull DependencyScope scope, boolean exported);

  @Nullable
  ModuleOrderEntry findModuleOrderEntry(@NotNull Module module);

  @Nullable
  LibraryOrderEntry findLibraryOrderEntry(@NotNull Library library);

  /**
   * Removes order entry from an order.
   */
  void removeOrderEntry(@NotNull OrderEntry orderEntry);

  void rearrangeOrderEntries(OrderEntry @NotNull [] newOrder);

  void clear();

  /**
   * Commits changes to a {@link ModuleRootManager}.
   * Should be invoked in a write action. After <code>commit()<code>, the model
   * becomes read-only.
   * <p>
   * Use of {@link ModuleRootModificationUtil#updateModel(Module, Consumer)} is recommended.
   */
  void commit();

  /**
   * Must be invoked for uncommitted models that are no longer needed.
   * <p>
   * Use of {@link ModuleRootModificationUtil#updateModel(Module, Consumer)} is recommended.
   */
  void dispose();

  /**
   * Returns library table with module libraries.
   * <p/>
   * <b>Note:</b> returned library table does not support listeners. Also, one shouldn't invoke 'commit()' or 'dispose()' methods on it,
   * it is automatically committed or disposed along with this {@link ModifiableRootModel} instance.
   *
   * @return library table to be modified
   */
  @NotNull
  LibraryTable getModuleLibraryTable();

  /**
   * Sets SDK for this module to a specific value.
   */
  void setSdk(@Nullable Sdk sdk);

  /**
   * Sets JDK name and type for this module.
   * To be used when SDK with this name and type does not exist (e.g. when importing module configuration).
   *
   * @param sdkName SDK name
   * @param sdkType SDK type
   */
  void setInvalidSdk(@NotNull String sdkName, @NotNull String sdkType);

  /**
   * Makes this module inheriting SDK from its project.
   */
  void inheritSdk();

  boolean isChanged();

  boolean isWritable();

  <T extends OrderEntry> void replaceEntryOfType(@NotNull Class<T> entryClass, T entry);

  @Nullable
  String getSdkName();

  boolean isDisposed();
}
