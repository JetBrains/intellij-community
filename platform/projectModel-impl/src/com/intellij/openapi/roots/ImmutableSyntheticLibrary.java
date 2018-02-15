// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

class ImmutableSyntheticLibrary extends SyntheticLibrary {

  private final Collection<VirtualFile> myBinaryRoots;
  private final Collection<VirtualFile> mySourceRoots;
  private final Set<VirtualFile> myExcludedRoots;
  private final Condition<VirtualFile> myExcludeCondition;

  ImmutableSyntheticLibrary(@NotNull Collection<VirtualFile> sourceRoots,
                            @NotNull Collection<VirtualFile> binaryRoots,
                            @NotNull Set<VirtualFile> excludedRoots,
                            @Nullable Condition<VirtualFile> excludeCondition) {
    mySourceRoots = ContainerUtil.unmodifiableOrEmptyCollection(sourceRoots);
    myBinaryRoots = ContainerUtil.unmodifiableOrEmptyCollection(binaryRoots);
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
    return myExcludeCondition;
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
}
