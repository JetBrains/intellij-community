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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A lightweight library definition comparing to {@link com.intellij.openapi.roots.libraries.Library}.
 * When provided by {@link AdditionalLibraryRootsProvider}, a library of this type contributes the followings:
 * <ul>
 *   <li>Source libraries roots ({@link #getSourceRoots()}) extends {@link com.intellij.psi.search.GlobalSearchScope#allScope(Project)}
 *   (in UI, "Project and Libraries" scope).
 *   Files contained in the returned roots are considered as library source files:
 *   {@link ProjectFileIndex#isInLibrarySource(VirtualFile)} returns {@code true} for them.
 *   <br>
 *   Unlike to {@code library.getFiles(OrderRootType.SOURCES)}, these source roots are not indexed and
 *   are not included in the classpath.</li>
 *   <li>An item in "External Libraries" in Project view if {@link #getName()} is not-null</li>
 * </ul>
 * @see AdditionalLibraryRootsProvider
 */
@ApiStatus.Experimental
public abstract class SyntheticLibrary {
  @Nullable
  public abstract String getName();

  @NotNull
  public abstract Collection<VirtualFile> getSourceRoots();

  @NotNull
  public static SyntheticLibrary newFixedLibrary(@Nullable String name, @NotNull Collection<VirtualFile> sourceRoots) {
    return new SyntheticLibrary() {
      @Nullable
      @Override
      public String getName() {
        return name;
      }

      @NotNull
      @Override
      public Collection<VirtualFile> getSourceRoots() {
        return sourceRoots;
      }
    };
  }
}
