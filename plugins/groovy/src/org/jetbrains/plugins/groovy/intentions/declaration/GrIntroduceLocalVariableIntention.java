package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
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
      if (((GrExpression)element).getType() != PsiType.VOID) {
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
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    setSelection(editor, getTargetExpression(element));
    new GrIntroduceVariableHandler().invoke(project, editor, element.getContainingFile(), null);
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

