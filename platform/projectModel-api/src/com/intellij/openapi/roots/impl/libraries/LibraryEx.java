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

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 *  @author dsl
 */
public interface LibraryEx extends Library {
  @NotNull
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
  @NotNull
  String[] getExcludedRootUrls();

  /**
   * @see #getExcludedRootUrls()
   * @return excluded directories
   */
  @NotNull
  VirtualFile[] getExcludedRoots();

  interface ModifiableModelEx extends ModifiableModel {
    void setProperties(LibraryProperties properties);

    LibraryProperties getProperties();

    void setKind(@NotNull PersistentLibraryKind<?> type);

    PersistentLibraryKind<?> getKind();

    /**
     * Add a URL to list of directories excluded from the library. The directory specified by {@code url} must be located under some
     * of the library roots.
     * @see LibraryEx#getExcludedRootUrls()
     * @param url URL of a directory to be excluded
     */
    void addExcludedRoot(@NotNull String url);

    boolean removeExcludedRoot(@NotNull String url);

    @NotNull
    String[] getExcludedRootUrls();
  }
}
