// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.BooleanSupplier;

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
 * <p>
 * <p>
 * Providing comparisonId in constructor and constant ExcludeFileCondition instead of getExcludeFileCondition
 * allows rescanning changes in library incrementally.
 *
 * @see AdditionalLibraryRootsProvider
 */
public abstract class SyntheticLibrary {

  private final @Nullable @NonNls String myComparisonId;
  protected final ExcludeFileCondition myConstantExcludeCondition;

  /**
   * Providing comparisonId in constructor and constant ExcludeFileCondition instead of getExcludeFileCondition
   * allows rescanning changes in library incrementally.
   *
   * @param comparisonId             should be different for all {@link SyntheticLibrary} provided by the same {@link AdditionalLibraryRootsProvider}
   * @param constantExcludeCondition must not depend on any data other than provided parameters, and should be very fast.
   *                                 All comments to {@link SyntheticLibrary#getExcludedRoots()} also relate to it.
   */
  protected SyntheticLibrary(@Nullable @NonNls String comparisonId, @Nullable ExcludeFileCondition constantExcludeCondition) {
    myComparisonId = comparisonId;
    myConstantExcludeCondition = constantExcludeCondition;
  }

  public SyntheticLibrary() {
    this(null, null);
  }

  public final @Nullable String getComparisonId() {
    return myComparisonId;
  }

  public abstract @NotNull Collection<VirtualFile> getSourceRoots();

  public @NotNull Collection<VirtualFile> getBinaryRoots() {
    return Collections.emptyList();
  }

  public @NotNull Set<VirtualFile> getExcludedRoots() {
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
   * <p>
   * NOTE 3: Non-null value blocks from incremental rescanning of library changes. Consider switching to constantExcludeCondition and
   * providing non-null comparisonId in constructor.
   * <p>
   * @deprecated Use {@link SyntheticLibrary#getUnitedExcludeCondition()} to get filtering condition,
   * and {@link SyntheticLibrary#myConstantExcludeCondition} to provide it, as it allows incremental rescan of the library.
   */
  @Deprecated
  public @Nullable Condition<? super VirtualFile> getExcludeFileCondition() {
    return null;
  }

  private @Nullable Condition<VirtualFile> getConstantExcludeConditionAsCondition() {
    if (myConstantExcludeCondition == null) return null;
    Collection<VirtualFile> allRoots = getAllRoots();
    return myConstantExcludeCondition.transformToCondition(allRoots);
  }

  public final @Nullable Condition<? super VirtualFile> getUnitedExcludeCondition() {
    Condition<? super VirtualFile> condition = getExcludeFileCondition();
    if (condition == null) return getConstantExcludeConditionAsCondition();
    Condition<VirtualFile> otherCondition = getConstantExcludeConditionAsCondition();
    if (otherCondition == null) return condition;
    return file -> condition.value(file) || otherCondition.value(file);
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

  public static @NotNull SyntheticLibrary newImmutableLibrary(@NotNull List<? extends VirtualFile> sourceRoots) {
    return newImmutableLibrary(sourceRoots, Collections.emptySet(), null);
  }

  /**
   * @see SyntheticLibrary#newImmutableLibrary(String, List, List, Set, ExcludeFileCondition)
   */
  public static @NotNull SyntheticLibrary newImmutableLibrary(@NotNull List<? extends VirtualFile> sourceRoots,
                                                     @NotNull Set<? extends VirtualFile> excludedRoots,
                                                     @Nullable Condition<? super VirtualFile> excludeCondition) {
    return newImmutableLibrary(sourceRoots, Collections.emptyList(), excludedRoots, excludeCondition);
  }

  /**
   * @see SyntheticLibrary#newImmutableLibrary(String, List, List, Set, ExcludeFileCondition)
   */
  public static @NotNull SyntheticLibrary newImmutableLibrary(@NotNull List<? extends VirtualFile> sourceRoots,
                                                     @NotNull List<? extends VirtualFile> binaryRoots,
                                                     @NotNull Set<? extends VirtualFile> excludedRoots,
                                                     @Nullable Condition<? super VirtualFile> excludeCondition) {
    return new ImmutableSyntheticLibrary(null, sourceRoots, binaryRoots, excludedRoots, excludeCondition, null);
  }

  /**
   * Providing comparisonId in constructor and constant ExcludeFileCondition instead of getExcludeFileCondition
   * allows rescanning changes in library incrementally. Consider this method as the best of all newImmutableLibrary.
   *
   * @param comparisonId     should be different for all libraries returned by the same {@link AdditionalLibraryRootsProvider},
   *                         and retained for the same library between its invocations to enable rescan its changes incrementally
   * @param excludeCondition should depend only on its parameters
   */
  public static @NotNull SyntheticLibrary newImmutableLibrary(@NotNull String comparisonId,
                                                     @NotNull List<? extends VirtualFile> sourceRoots,
                                                     @NotNull List<? extends VirtualFile> binaryRoots,
                                                     @NotNull Set<? extends VirtualFile> excludedRoots,
                                                     @Nullable ExcludeFileCondition excludeCondition) {
    return new ImmutableSyntheticLibrary(comparisonId, sourceRoots, binaryRoots, excludedRoots, null, excludeCondition);
  }

  @Unmodifiable
  public final @NotNull Collection<VirtualFile> getAllRoots() {
    return getRoots(true, true);
  }

  @Unmodifiable
  private @NotNull Collection<VirtualFile> getRoots(boolean includeSources, boolean includeBinaries) {
    if (includeSources && includeBinaries) {
      Collection<VirtualFile> sourceRoots = getSourceRoots();
      Collection<VirtualFile> binaryRoots = getBinaryRoots();
      if (binaryRoots.isEmpty()) {
        return sourceRoots;
      }
      if (sourceRoots.isEmpty()) {
        return binaryRoots;
      }
      return ContainerUtil.union(sourceRoots, binaryRoots);
    }
    if (includeSources) {
      return getSourceRoots();
    }
    if (includeBinaries) {
      return getBinaryRoots();
    }
    return Collections.emptySet();
  }

  public final boolean contains(@NotNull VirtualFile file, boolean includeSources, boolean includeBinaries) {
    Set<? extends VirtualFile> roots = asSet(getRoots(includeSources, includeBinaries));
    return VfsUtilCore.isUnder(file, roots) && !VfsUtilCore.isUnder(file, getExcludedRoots());
  }

  public final boolean contains(@NotNull VirtualFile file) {
    return contains(file, true, true);
  }

  private static @NotNull Set<? extends VirtualFile> asSet(@NotNull Collection<? extends VirtualFile> collection) {
    return collection instanceof Set ? (Set<? extends VirtualFile>)collection : new HashSet<>(collection);
  }

  private static @NotNull List<? extends VirtualFile> asList(@NotNull Collection<? extends VirtualFile> collection) {
    return collection instanceof List ? (List<? extends VirtualFile>)collection : new ArrayList<>(collection);
  }

  public interface ExcludeFileCondition {
    boolean shouldExclude(boolean isDir,
                          @NotNull String filename,
                          @NotNull BooleanSupplier isRoot,
                          @NotNull BooleanSupplier isStrictRootChild,
                          @NotNull BooleanSupplier hasParentNotGrandparent);

    default @NotNull Condition<VirtualFile> transformToCondition(@NotNull Collection<? extends VirtualFile> allRoots) {
      return file -> shouldExclude(file.isDirectory(), file.getName(),
                                   () -> allRoots.contains(file),
                                   () -> {
                                     VirtualFile parent = file.getParent();
                                     return parent != null && allRoots.contains(parent);
                                   },
                                   () -> {
                                     VirtualFile parent = file.getParent();
                                     return parent == null || parent.getParent() != null;
                                   });
    }
  }
}
