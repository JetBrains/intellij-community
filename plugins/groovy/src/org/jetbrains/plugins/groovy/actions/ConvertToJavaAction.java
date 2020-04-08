// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.ConvertToJavaHandler;

import static org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector.isScriptFile;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaAction extends BaseRefactoringAction {

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return isEnabledOnElements(new PsiElement[]{element});
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return GroovyLanguage.INSTANCE == language;
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    for (PsiElement element : elements) {
      final PsiFile containingFile = element.getContainingFile();
      if (!(containingFile instanceof GroovyFile) || isScriptFile((GroovyFile)containingFile)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new ConvertToJavaHandler();
  }
}
