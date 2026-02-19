// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

@ApiStatus.Internal
public final class PsiPatchBaseDirectoryDetector extends PatchBaseDirectoryDetector {
  private final Project myProject;

  public PsiPatchBaseDirectoryDetector(final Project project) {
    myProject = project;
  }

  @Override
  public Collection<VirtualFile> findFiles(final String fileName) {
    return FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(myProject));
  }
}
