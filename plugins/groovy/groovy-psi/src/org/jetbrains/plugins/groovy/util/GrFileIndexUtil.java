// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

public final class GrFileIndexUtil {

  public static boolean isGroovySourceFile(@NotNull PsiFile file) {
    return file instanceof GroovyFileBase && isGroovySourceFile((GroovyFileBase)file);
  }

  public static boolean isGroovySourceFile(@NotNull GroovyFileBase file) {
    return isInSourceFiles(file.getVirtualFile(), file.getProject());
  }

  private static boolean isInSourceFiles(@Nullable VirtualFile file, @NotNull Project project) {
    if (file != null && !(file instanceof LightVirtualFile)) {
      final FileIndexFacade index = FileIndexFacade.getInstance(project);
      if (index.isInSource(file) || index.isInLibraryClasses(file)) {
        return true;
      }
    }
    return false;
  }
}
