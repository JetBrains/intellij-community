// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Collection;


public class PsiPatchBaseDirectoryDetector extends PatchBaseDirectoryDetector {
  private final Project myProject;

  public PsiPatchBaseDirectoryDetector(final Project project) {
    myProject = project;
  }

  @Override
  public Collection<VirtualFile> findFiles(final String fileName) {
    return FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(myProject));
  }
}
