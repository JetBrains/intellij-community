// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.HashSet;
import java.util.Set;

public class ConvertToStaticHandler implements RefactoringActionHandler {

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invokeInner(project, new PsiElement[]{file});
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    invokeInner(project, elements);
  }

  private static void invokeInner(Project project, PsiElement[] elements) {
    Set<GroovyFile> files = collectFilesForProcessing(elements);

    new ConvertToStaticProcessor(project, files.toArray(GroovyFile.EMPTY_ARRAY)).run();
  }

  public static Set<GroovyFile> collectFilesForProcessing(PsiElement @NotNull [] elements) {
    Set<GroovyFile> files = new HashSet<>();
    for (PsiElement element : elements) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof GroovyFile) {
        files.add((GroovyFile)containingFile);
      }
    }
    return files;
  }
}
