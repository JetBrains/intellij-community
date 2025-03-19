// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.io.File;
import java.util.regex.Pattern;

public abstract class GroovyUtils {

  public static File[] getFilesInDirectoryByPattern(String dirPath, final String patternString) {
    final Pattern pattern = Pattern.compile(patternString);
    return LibrariesUtil.getFilesInDirectoryByPattern(dirPath, pattern);
  }

  public static @Nullable GrTypeDefinition getPublicClass(@NotNull Project project, @NotNull VirtualFile file) {
    return getPublicClass(file, PsiManager.getInstance(project));
  }

  public static @Nullable GrTypeDefinition getPublicClass(@Nullable VirtualFile virtualFile, @NotNull PsiManager manager) {
    if (virtualFile == null) return null;

    PsiElement psiFile = manager.findFile(virtualFile);
    if (psiFile instanceof PsiCompiledFile) {
      psiFile = psiFile.getNavigationElement();
    }

    if (psiFile instanceof GroovyFile) {
      return getClassDefinition((GroovyFile)psiFile);
    }

    return null;
  }

  public static @Nullable GrTypeDefinition getClassDefinition(@NotNull GroovyFile groovyFile) {
    String fileName = groovyFile.getName();
    int idx = fileName.lastIndexOf('.');
    if (idx < 0) return null;

    return getClassDefinition(groovyFile, fileName.substring(0, idx));
  }

  public static @Nullable GrTypeDefinition getClassDefinition(@NotNull GroovyFile groovyFile, @NotNull String classSimpleName) {
    for (GrTypeDefinition definition : (groovyFile).getTypeDefinitions()) {
      if (classSimpleName.equals(definition.getName())) {
        return definition;
      }
    }

    return null;
  }
}
