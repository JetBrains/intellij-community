// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.internal;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

final class IconsGeneratedSourcesFilter extends GeneratedSourcesFilter {
  @Override
  public boolean isGeneratedSource(final @NotNull VirtualFile file, final @NotNull Project project) {
    if (file.getName().endsWith("Icons.java")) {
      return ReadAction.compute(() -> {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          for (PsiClass aClass : ((PsiJavaFile)psiFile).getClasses()) {
            if (aClass.isValid() && aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
              PsiDocComment comment = aClass.getDocComment();
              if (comment == null) return false;
              String docText = comment.getText();
              return docText.contains("NOTE THIS FILE IS AUTO-GENERATED") &&
                     docText.contains("DO NOT EDIT IT BY HAND");
            }
          }
        }
        return false;
      });
    }
    return false;
  }
}
