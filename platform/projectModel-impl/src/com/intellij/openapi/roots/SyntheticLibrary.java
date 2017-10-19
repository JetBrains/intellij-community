// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A lightweight library definition comparing to {@link com.intellij.openapi.roots.libraries.Library}.
 * When provided by {@link AdditionalLibraryRootsProvider}, a library of this type contributes the followings:
 * <ul>
 *   <li>Source libraries roots ({@link #getSourceRoots()}) extend {@link com.intellij.psi.search.GlobalSearchScope#allScope(Project)}
 *   (in UI, "Project and Libraries" scope).
 *   Files contained inside the returned roots are considered as library source files:
 *   {@link ProjectFileIndex#isInLibrarySource(VirtualFile)} returns {@code true} for them.
 *   <br>
 *   <li>File exclusions for provided sources roots ({@link #getExcludedRoots()}).
 *   These roots won't be indexed and will be handled as {@link LibraryEx#getExcludedRoots()}</li>
 *   <br>
 *   Generally, {@link #getSourceRoots()} are handled similarly to {@code library.getFiles(OrderRootType.SOURCES)}.
 *   <li>An item in "External Libraries" in Project view if library returns true on {@link #isShowInExternalLibrariesNode()} and
 *   is instance of {@link ItemPresentation}</li>.
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

  @NotNull
  public Set<VirtualFile> getExcludedRoots() {
    return Collections.emptySet();
  }

  /**
   * @return a condition for excluding file from a library or {@code null}
   * E.g. you can exclude all non-java file by returning {@code file -> !file.getName().endsWith(".java")}
   * <p>
   * Excluding directory leads to excluding all its content recursively.
   * <p>
   * NOTE: The condition is participating in building indexing and project model,
   * it must be bloody fast in order not to affect overall IDE performance.
   * <p>
   * NOTE 2: Try not to use file.getFileType() method since it might load file's content to know the type,
   * which will try to load encoding and guess files project which is lead to SOE.
   */
  @Nullable
  public Condition<VirtualFile> getExcludeFileCondition() {
    return null;
  }

  public boolean isShowInExternalLibrariesNode() {
    return this instanceof ItemPresentation;
  }

  /**
   * This method is vital if this library is shown under "External Libraries" (the library should implement ItemPresentation for that).
   * In this case, each {@link com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode#getChildren()} invocation will create a new
   * {@link com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode} instance passing this library as a value.
   * In order to figure out if "External Library" children are updated or not, AbstractTreeUi uses
   * node's equals/hashCode methods which in turn depend on this library's equals/hashCode methods:
   * see {@link com.intellij.ide.util.treeView.AbstractTreeNode#hashCode()}.
   * <p>
   * Please make sure that two SyntheticLibrary instances are equal if they reference the same state. Otherwise, constant UI updates
   * will degrade performance.
   * Consider implementing a better equals/hashCode if needed or instantiate {@link SyntheticLibrary} only if state
   * changed (use some caching in {@link AdditionalLibraryRootsProvider#getAdditionalProjectLibraries(Project)}).
   */
  @Override
  public abstract boolean equals(Object o);

  /**
   * @see #equals(Object) javadoc
   */
  @Override
  public abstract int hashCode();

  @NotNull
  public static SyntheticLibrary newImmutableLibrary(@NotNull Collection<VirtualFile> sourceRoots) {
    return newImmutableLibrary(sourceRoots, Collections.emptySet(), null);
  }

  @NotNull
  public static SyntheticLibrary newImmutableLibrary(@NotNull Collection<VirtualFile> sourceRoots,
                                                     @NotNull Set<VirtualFile> excludedRoots,
                                                     @Nullable Condition<VirtualFile> excludeCondition) {
    return new SyntheticLibrary() {
      @NotNull
      @Override
      public Collection<VirtualFile> getSourceRoots() {
        return sourceRoots;
      }

      @NotNull
      @Override
      public Set<VirtualFile> getExcludedRoots() {
        return excludedRoots;
      }

      @Nullable
      @Override
      public Condition<VirtualFile> getExcludeFileCondition() {
        return excludeCondition;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyntheticLibrary library = (SyntheticLibrary)o;
        if (!sourceRoots.equals(library.getSourceRoots())) return false;
        if (!excludedRoots.equals(library.getExcludedRoots())) return false;
        if (excludeCondition != null ? !excludeCondition.equals(library.getExcludeFileCondition())
                                     : library.getExcludeFileCondition() != null) {
          return false;
        }
        return true;
      }

      @Override
      public int hashCode() {
        int result = sourceRoots.hashCode();
        result = 31 * result + excludedRoots.hashCode();
        result = 31 * result + (excludeCondition != null ? excludeCondition.hashCode() : 0);
        return result;
      }
    };
  }
}
