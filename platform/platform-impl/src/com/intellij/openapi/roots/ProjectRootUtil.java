// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class ProjectRootUtil {
  public static @NotNull VirtualFile findSymlinkedFileInContent(@NotNull Project project, @NotNull VirtualFile forFile) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    if (scope.contains(forFile)) return forFile;

    VirtualFile canonicalForFile = forFile.getCanonicalFile();
    if (canonicalForFile == null) canonicalForFile = forFile;

    Collection<VirtualFile> projectFiles =
      FilenameIndex.getVirtualFilesByName(canonicalForFile.getName(), scope);

    for (VirtualFile eachContentFile : projectFiles) {
      if (canonicalForFile.equals(eachContentFile.getCanonicalFile())) return eachContentFile;
    }

    return forFile;
  }
}
