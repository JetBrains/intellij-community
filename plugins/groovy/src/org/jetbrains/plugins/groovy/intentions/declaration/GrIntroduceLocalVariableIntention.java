/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
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
      if (!PsiType.VOID.equals(((GrExpression)element).getType())) {
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
  protected void processIntention(@NotNull final PsiElement element, final Project project, final Editor editor) throws IncorrectOperationException {
    setSelection(editor, getTargetExpression(element));
    final PsiFile file = element.getContainingFile();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new GrIntroduceVariableHandler().invoke(project, editor, file, null);
      }
    });
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (element == null) {
          return false;
        }
        return getTargetExpression(element) != null;
      }
    };
  }
}

