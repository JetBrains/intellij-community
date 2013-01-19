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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;

/**
 * Groovy Introduce local variable intention.
 *
 * @author siosio
 */
public class GrIntroduceLocalVariableIntention extends Intention {

  protected @NotNull PsiElement getTargetExpression(@NotNull PsiElement element) {
    PsiElement expression = PsiTreeUtil.getParentOfType(element, GrExpression.class);
    return expression == null ? element : getTargetExpression(expression);
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
        GrExpression expression = PsiTreeUtil.getParentOfType(element, GrExpression.class);
        if (expression == null || expression.getType() == PsiType.VOID) {
          return false;
        }
        GrVariableDeclaration statement = PsiTreeUtil.getParentOfType(expression, GrVariableDeclaration.class);
        if (statement != null) {
          return false;
        }
        GrAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(expression, GrAssignmentExpression.class);
        return assignmentExpression == null;
      }
    };
  }
}

