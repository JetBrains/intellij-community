// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DefaultVcsRootChecker extends VcsRootChecker {
  @NotNull private final AbstractVcs myVcs;
  @Nullable private final VcsDescriptor myVcsDescriptor;

  DefaultVcsRootChecker(@NotNull AbstractVcs vcs, @Nullable VcsDescriptor vcsDescriptor) {
    myVcs = vcs;
    myVcsDescriptor = vcsDescriptor;
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return myVcs.getKeyInstanceMethod();
  }

  @Override
  public boolean isRoot(@NotNull VirtualFile file) {
    if (myVcsDescriptor == null) return false;
    return myVcsDescriptor.probablyUnderVcs(file);
  }

  @Override
  public boolean validateRoot(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public boolean isVcsDir(@NotNull String dirName) {
    if (myVcsDescriptor == null) return false;
    return myVcsDescriptor.matchesVcsDirPattern(dirName);
  }

  @Override
  public boolean areChildrenValidMappings() {
    if (myVcsDescriptor == null) return false;
    return myVcsDescriptor.areChildrenValidMappings();
  }
}
