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

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * A lightweight library definition comparing to {@link com.intellij.openapi.roots.libraries.Library}.
 * When provided by {@link AdditionalLibraryRootsProvider}, a library of this type contributes the followings:
 * <ul>
 *   <li>Source libraries roots ({@link #getSourceRoots()}) extend {@link com.intellij.psi.search.GlobalSearchScope#allScope(Project)}
 *   (in UI, "Project and Libraries" scope).
 *   Files contained inside the returned roots are considered as library source files:
 *   {@link ProjectFileIndex#isInLibrarySource(VirtualFile)} returns {@code true} for them.
 *   <br>
 *   Generally, {@link #getSourceRoots()} are handled similarly to {@code library.getFiles(OrderRootType.SOURCES)}.
 *   <li>An item in "External Libraries" in Project view if library is instance of {@link ItemPresentation}</li>.
 * </ul>
 * <p/>
 * To decorate a child node of "External Libraries" node in Project view consider implementing corresponding interfaces:
 * <ul>
 *   <li>{@link ItemPresentation} or {@link com.intellij.navigation.ColoredItemPresentation}</li>
 *   <li>{@link com.intellij.navigation.LocationPresentation}</li>
 *   <li>{@link com.intellij.pom.Navigatable} or {@link com.intellij.pom.NavigatableWithText}</li>
 * </ul>
 * @see AdditionalLibraryRootsProvider
 */
@SuppressWarnings("JavadocReference")
@ApiStatus.Experimental
public abstract class SyntheticLibrary {

  @NotNull
  public abstract Collection<VirtualFile> getSourceRoots();

  /**
   * This method is vital if this library is shown under "External Libraries" (the library should implement ItemPresentation for that).
   * In this case, each {@link com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode#getChildren()} invocation will create a new
   * {@link com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode} instance passing this library as a value.
   * In order to figure out if "External Library" children are updated or not, AbstractTreeUi uses
   * node's equals/hashCode methods which in turn depend on this library's equals/hashCode methods:
   * see {@link com.intellij.ide.util.treeView.AbstractTreeNode#hashCode()}.
   *
   * Please make sure that two SyntheticLibrary instances are equal if they reference the same state. Otherwise, constant UI updates
   * will degrade performance.
   * Consider implementing a better equals/hashCode if needed or instantiate {@link SyntheticLibrary} only if state
   * changed (use some caching in {@link AdditionalLibraryRootsProvider#getAdditionalProjectLibraries(Project)}).
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SyntheticLibrary library = (SyntheticLibrary)o;
    return getSourceRoots().equals(library.getSourceRoots());
  }

  /**
   * @see #equals(Object) javadoc
   */
  @Override
  public int hashCode() {
    return getSourceRoots().hashCode();
  }

  @NotNull
  public static SyntheticLibrary newImmutableLibrary(@NotNull Collection<VirtualFile> sourceRoots) {
    return new SyntheticLibrary() {
      @NotNull
      @Override
      public Collection<VirtualFile> getSourceRoots() {
        return sourceRoots;
      }
    };
  }
}
