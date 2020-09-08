// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaHandler implements RefactoringActionHandler {
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invokeInner(project, new PsiElement[]{file}, editor);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    Editor editor = null;
    if (dataContext != null) {
      editor = CommonDataKeys.EDITOR.getData(dataContext);
    }
    invokeInner(project, elements, editor);
  }

  private static void invokeInner(Project project, PsiElement[] elements, Editor editor) {
    Set<GroovyFile> files = new HashSet<>();

    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile)) {
        element = element.getContainingFile();
      }

      if (element instanceof GroovyFile) {
        files.add((GroovyFile)element);
      }
      else {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          CommonRefactoringUtil.showErrorHint(
            project, editor,
            GroovyRefactoringBundle.message("convert.to.java.can.work.only.with.groovy"),
            GroovyRefactoringBundle.message("convert.to.java.refactoring.name"),
            null
          );
          return;
        }
      }
    }

    new ConvertToJavaProcessor(project, files.toArray(GroovyFile.EMPTY_ARRAY)).run();
  }
}
