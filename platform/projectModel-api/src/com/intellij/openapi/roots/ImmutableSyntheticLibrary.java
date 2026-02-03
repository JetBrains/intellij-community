// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ApiStatus.Internal
public class ImmutableSyntheticLibrary extends SyntheticLibrary {
  private final List<VirtualFile> mySourceRoots;
  private final List<VirtualFile> myBinaryRoots;
  private final Set<VirtualFile> myExcludedRoots;
  private final Condition<? super VirtualFile> myExcludeCondition;
  private final int hashCode;

  public ImmutableSyntheticLibrary(@Nullable String comparisonId,
                                   @NotNull List<? extends VirtualFile> sourceRoots,
                                   @NotNull List<? extends VirtualFile> binaryRoots,
                                   @NotNull Set<? extends VirtualFile> excludedRoots,
                                   @Nullable Condition<? super VirtualFile> excludeCondition,
                                   @Nullable ExcludeFileCondition constantCondition) {
    super(comparisonId, constantCondition);
    mySourceRoots = List.copyOf(sourceRoots);
    myBinaryRoots = List.copyOf(binaryRoots);
    myExcludedRoots = ContainerUtil.unmodifiableOrEmptySet(excludedRoots);
    myExcludeCondition = excludeCondition;
    hashCode = 31 * (31 * sourceRoots.hashCode() + binaryRoots.hashCode()) + excludedRoots.hashCode();
  }

  @Override
  public @NotNull @Unmodifiable Collection<VirtualFile> getSourceRoots() {
    return mySourceRoots;
  }

  @Override
  public @NotNull @Unmodifiable Collection<VirtualFile> getBinaryRoots() {
    return myBinaryRoots;
  }

  @Override
  public @NotNull @Unmodifiable Set<VirtualFile> getExcludedRoots() {
    return myExcludedRoots;
  }

  @Override
  public @Nullable Condition<? super VirtualFile> getExcludeFileCondition() {
    return myExcludeCondition;
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
}
