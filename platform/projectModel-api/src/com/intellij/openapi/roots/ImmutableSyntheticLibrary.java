// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Predicate;

@ApiStatus.Internal
class ImmutableSyntheticLibrary extends SyntheticLibrary {
  private final List<VirtualFile> mySourceRoots;
  private final List<VirtualFile> myBinaryRoots;
  private final Set<VirtualFile> myExcludedRoots;
  private final Predicate<? super VirtualFile> myExcludeCondition;
  private final int hashCode;

  ImmutableSyntheticLibrary(@Nullable String comparisonId,
                            @NotNull List<? extends VirtualFile> sourceRoots,
                            @NotNull List<? extends VirtualFile> binaryRoots,
                            @NotNull Set<? extends VirtualFile> excludedRoots,
                            @Nullable Predicate<? super VirtualFile> excludeCondition,
                            @Nullable ExcludeFileCondition constantCondition) {
    super(comparisonId, constantCondition);
    mySourceRoots = immutableOrEmptyList(sourceRoots);
    myBinaryRoots = immutableOrEmptyList(binaryRoots);
    myExcludedRoots = ContainerUtil.unmodifiableOrEmptySet(excludedRoots);
    myExcludeCondition = excludeCondition;
    hashCode = Objects.hash(mySourceRoots, myBinaryRoots, myExcludedRoots, myExcludeCondition);
  }

  /**
   * @deprecated use {@link ImmutableSyntheticLibrary#ImmutableSyntheticLibrary(String, List, List, Set, Predicate, ExcludeFileCondition)} instead
   */
  @Deprecated
  ImmutableSyntheticLibrary(@Nullable String comparisonId,
                            @NotNull List<? extends VirtualFile> sourceRoots,
                            @NotNull List<? extends VirtualFile> binaryRoots,
                            @NotNull Set<? extends VirtualFile> excludedRoots,
                            @Nullable Condition<? super VirtualFile> excludeCondition,
                            @Nullable ExcludeFileCondition constantCondition) {
    this(comparisonId, sourceRoots, binaryRoots, excludedRoots, (Predicate<? super VirtualFile>) excludeCondition, constantCondition);
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getSourceRoots() {
    return mySourceRoots;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getBinaryRoots() {
    return myBinaryRoots;
  }

  @NotNull
  @Override
  public Set<VirtualFile> getExcludedRoots() {
    return myExcludedRoots;
  }

  @Nullable
  @Override
  public Condition<VirtualFile> getExcludeFileCondition() {
    return (Condition<VirtualFile>)myExcludeCondition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ImmutableSyntheticLibrary library = (ImmutableSyntheticLibrary)o;
    if (!Objects.equals(getComparisonId(), library.getComparisonId())) return false;
    if (!mySourceRoots.equals(library.getSourceRoots())) return false;
    if (!myBinaryRoots.equals(library.getBinaryRoots())) return false;
    if (!myExcludedRoots.equals(library.getExcludedRoots())) return false;
    return Objects.equals(myExcludeCondition, library.getExcludeFileCondition());
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @NotNull
  @Unmodifiable
  private static <E> List<E> immutableOrEmptyList(@NotNull List<? extends E> list) {
    return list.isEmpty() ? Collections.emptyList() : List.copyOf(list);
  }
}
