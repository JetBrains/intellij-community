// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class DefaultVcsRootChecker extends VcsRootChecker {
  @NotNull private final AbstractVcs myVcs;
  private final VcsDescriptor myVcsDescriptor;

  DefaultVcsRootChecker(@NotNull AbstractVcs vcs) {
    myVcs = vcs;
    myVcsDescriptor = ProjectLevelVcsManager.getInstance(vcs.getProject()).getDescriptor(vcs.getName());
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return myVcs.getKeyInstanceMethod();
  }

  @Override
  public boolean isRoot(@NotNull VirtualFile file) {
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
