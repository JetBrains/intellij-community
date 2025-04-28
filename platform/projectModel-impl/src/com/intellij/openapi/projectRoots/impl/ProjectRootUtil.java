// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ProjectRootUtil {
  private static final Logger LOG = Logger.getInstance(ProjectRootUtil.class);

  private ProjectRootUtil() {
  }

  private static PsiDirectory @NotNull [] convertRoots(@NotNull Project project, VirtualFile @NotNull [] roots) {
    return convertRoots(PsiManagerEx.getInstanceEx(project).getFileManager(), roots);
  }

  private static PsiDirectory @NotNull [] convertRoots(@NotNull FileManager fileManager, VirtualFile @NotNull [] roots) {
    List<PsiDirectory> dirs = new ArrayList<>();

    for (VirtualFile root : roots) {
      if (!root.isValid()) {
        LOG.error("Root " + root + " is not valid!");
      }
      PsiDirectory dir = fileManager.findDirectory(root);
      if (dir != null) {
        dirs.add(dir);
      }
    }

    return dirs.toArray(PsiDirectory.EMPTY_ARRAY);
  }

  public static PsiDirectory @NotNull [] getSourceRootDirectories(@NotNull Project project) {
    VirtualFile[] files = OrderEnumerator.orderEntries(project).sources().usingCache().getRoots();
    return convertRoots(project, files);
  }

  public static PsiDirectory @NotNull [] getAllContentRoots(@NotNull Project project) {
    VirtualFile[] files = ProjectRootManager.getInstance(project).getContentRootsFromAllModules();
    return convertRoots(project, files);
  }
}
