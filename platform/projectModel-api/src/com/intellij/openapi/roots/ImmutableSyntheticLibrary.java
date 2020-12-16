// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
class ImmutableSyntheticLibrary extends SyntheticLibrary {

  private final List<VirtualFile> mySourceRoots;
  private final List<VirtualFile> myBinaryRoots;
  private final Set<VirtualFile> myExcludedRoots;
  private final Condition<? super VirtualFile> myExcludeCondition;

  ImmutableSyntheticLibrary(@NotNull List<? extends VirtualFile> sourceRoots,
                            @NotNull List<? extends VirtualFile> binaryRoots,
                            @NotNull Set<? extends VirtualFile> excludedRoots,
                            @Nullable Condition<? super VirtualFile> excludeCondition) {
    mySourceRoots = immutableOrEmptyList(sourceRoots);
    myBinaryRoots = immutableOrEmptyList(binaryRoots);
    myExcludedRoots = ContainerUtil.unmodifiableOrEmptySet(excludedRoots);
    myExcludeCondition = excludeCondition;
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
    if (!mySourceRoots.equals(library.getSourceRoots())) return false;
    if (!myBinaryRoots.equals(library.getBinaryRoots())) return false;
    if (!myExcludedRoots.equals(library.getExcludedRoots())) return false;
    if (!Objects.equals(myExcludeCondition, library.getExcludeFileCondition())) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySourceRoots, myBinaryRoots, myExcludedRoots, myExcludeCondition);
  }

  @NotNull
  private static <E> List<E> immutableOrEmptyList(@NotNull List<? extends E> list) {
    return list.isEmpty() ? Collections.emptyList() : ContainerUtil.immutableList(list);
  } 
}
