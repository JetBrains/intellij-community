// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
final class GradleBuildClasspathResolveScopeEnlarger extends ResolveScopeEnlarger {
  @Override
  public SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    if (ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(file)) {
      return null;
    }
    GradleClassFinder gradleClassFinder = PsiElementFinder.EP.findExtension(GradleClassFinder.class, project);
    if (gradleClassFinder == null) {
      return null;
    }
    final List<VirtualFile> roots = gradleClassFinder.calcClassRoots();
    for (VirtualFile root : roots) {
      if (VfsUtilCore.isAncestor(root, file, true)) {
        return NonClasspathDirectoriesScope.compose(roots);
      }
    }
    return null;
  }
}
