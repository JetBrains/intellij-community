// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;

/**
 * Groovy Introduce local variable intention.
 *
 * @author siosio
 */
public class GrIntroduceLocalVariableIntention extends Intention {

  protected PsiElement getTargetExpression(@NotNull PsiElement element) {
    if (isTargetVisible(element)) {
      return element;
    }
    PsiElement expression = PsiTreeUtil.getParentOfType(element, GrExpression.class);
    return expression == null ? null : getTargetExpression(expression);
  }

  private static boolean isTargetVisible(PsiElement element) {
    if (PsiUtil.isExpressionStatement(element) && element instanceof GrExpression) {
      if (!PsiTypes.voidType().equals(((GrExpression)element).getType())) {
        if (PsiTreeUtil.getParentOfType(element, GrAssignmentExpression.class) == null) {
          return true;
        }
      }
    }
    return false;
  }

  protected void setSelection(Editor editor, PsiElement element) {
    int offset = element.getTextOffset();
    int length = element.getTextLength();
    editor.getSelectionModel().setSelection(offset, offset + length);
  }

  @Override
  protected void processIntention(final @NotNull PsiElement element, final @NotNull Project project, final Editor editor) throws IncorrectOperationException {
    setSelection(editor, getTargetExpression(element));
    final PsiFile file = element.getContainingFile();
    new GrIntroduceVariableHandler().invoke(project, editor, file, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return getTargetExpression(element) != null;
      }
    };
  }
}

