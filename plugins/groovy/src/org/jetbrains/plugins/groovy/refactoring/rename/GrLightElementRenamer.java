// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenameableLightElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author Maxim.Medvedev
 */
public class GrLightElementRenamer extends RenamePsiElementProcessor {

  @Override
  public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
    String message =
      RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("rename.is.not.applicable.to.implicit.elements"));
    CommonRefactoringUtil.showErrorHint(element.getProject(), editor, message, RefactoringBundle.message("rename.title"), null);
    return null;
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (!(element instanceof LightElement)) return false;
    if (element instanceof GrRenameableLightElement) return false;
    final Language language = element.getLanguage();
    return GroovyLanguage.INSTANCE.equals(language);
  }
}
