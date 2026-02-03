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

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

@ApiStatus.NonExtendable
public interface LibraryEx extends Library {
  @NotNull
  @Unmodifiable
  List<String> getInvalidRootUrls(@NotNull OrderRootType type);

  boolean isDisposed();

  @NotNull
  @Override
  ModifiableModelEx getModifiableModel();

  @Nullable
  PersistentLibraryKind<?> getKind();

  LibraryProperties getProperties();

  /**
   * Returns URLs of directories under the library roots which are excluded from the library. Files under these directories
   * won't be counted as belonging to this library so they won't be indexed.
   * @return URLs of excluded directories
   */
  String @NotNull [] getExcludedRootUrls();

  /**
   * @see #getExcludedRootUrls()
   * @return excluded directories
   */
  VirtualFile @NotNull [] getExcludedRoots();

  @Nullable("will return non-null value only for module level libraries")
  Module getModule();

  /**
   * In case of modifiable library returns origin library it was created from
   */
  @ApiStatus.Internal
  @Nullable
  Library getSource();

  interface ModifiableModelEx extends ModifiableModel {
    void setProperties(LibraryProperties properties);

    LibraryProperties getProperties();

    void setKind(@Nullable PersistentLibraryKind<?> type);

    PersistentLibraryKind<?> getKind();

    /**
     * It's supposed that the external source is set when the library is created. This method is used internally to fix the project 
     * configuration if this information was lost.
     */
    @ApiStatus.Internal
    void setExternalSource(@NotNull ProjectModelExternalSource externalSource);

    /**
     * Replaces custom kind by a special 'unknown' kind if it was set. 
     * This method is called by the platform when a plugin which provides the custom kind is dynamically unloaded.
     */
    @ApiStatus.Internal
    void forgetKind();

    /**
     * Restores original kind which was converted to 'unknown' by {@link #forgetKind()}.  
     * This method is called by the platform when a plugin which provides the custom kind is dynamically loaded.
     */
    @ApiStatus.Internal
    void restoreKind();

    /**
     * Add a URL to list of directories excluded from the library. The directory specified by {@code url} must be located under some
     * of the library roots.
     * @see LibraryEx#getExcludedRootUrls()
     * @param url URL of a directory to be excluded
     */
    void addExcludedRoot(@NotNull String url);

    boolean removeExcludedRoot(@NotNull String url);

    String @NotNull [] getExcludedRootUrls();
  }
}
