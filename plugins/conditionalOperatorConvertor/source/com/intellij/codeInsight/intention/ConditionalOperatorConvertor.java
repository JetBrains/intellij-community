package com.intellij.codeInsight.intention;

import com.intellij.psi.JavaTokenType;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
@NonNls public class ConditionalOperatorConvertor implements ProjectComponent, IntentionAction {

  public static ConditionalOperatorConvertor getInstance(Project project) {
    return project.getComponent(ConditionalOperatorConvertor.class);
  }

  public ConditionalOperatorConvertor(Project project, IntentionManager intentionManager) {
    intentionManager.registerIntentionAndMetaData(this, "Conditional Operator");
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "TernaryOperatorConverter";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getText() {
    return "Convert ternary operator to if statement";
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    if (!element.isWritable()) return false;

    if (element instanceof PsiJavaToken) {
      final PsiJavaToken token = ((PsiJavaToken)element);
      if (token.getTokenType() != JavaTokenType.QUEST) return false;
      if (token.getParent() instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = ((PsiConditionalExpression)token.getParent());
        if (conditionalExpression.getThenExpression() == null
            || conditionalExpression.getElseExpression() == null) {
          return false;
        }
        return true;
      }
      return false;
    }
    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) PsiTreeUtil.getParentOfType(element,
                                                                                                            PsiConditionalExpression.class, false);
    if (conditionalExpression == null) return;
    if (conditionalExpression.getThenExpression() == null || conditionalExpression.getElseExpression() == null) return;

    final PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();

    PsiElement originalStatement = PsiTreeUtil.getParentOfType(conditionalExpression, PsiStatement.class, false);
    while (originalStatement instanceof PsiForStatement) {
      originalStatement = PsiTreeUtil.getParentOfType(originalStatement, PsiStatement.class, true);
    }
    if (originalStatement == null) return;

    // Maintain declrations
    if (originalStatement instanceof PsiDeclarationStatement) {
      final PsiDeclarationStatement declaration = ((PsiDeclarationStatement)originalStatement);
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      PsiLocalVariable variable = null;
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable && PsiTreeUtil.isAncestor(declaredElement, conditionalExpression, true)) {
          variable = (PsiLocalVariable)declaredElement;
          break;
        }
      }
      if (variable == null) return;
      variable.normalizeDeclaration();
      final Object marker = new Object();
      PsiTreeUtil.mark(conditionalExpression, marker);
      PsiExpressionStatement statement =
        (PsiExpressionStatement)factory.createStatementFromText(variable.getName() + " = 0;", null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(project).reformat(statement);
      ((PsiAssignmentExpression)statement.getExpression()).getRExpression().replace(variable.getInitializer());
      variable.getInitializer().delete();
      final PsiElement variableParent = variable.getParent();
      originalStatement = variableParent.getParent().addAfter(statement, variableParent);
      conditionalExpression = (PsiConditionalExpression)PsiTreeUtil.releaseMark(originalStatement, marker);
    }

    // create then and else branches
    final PsiElement[] originalElements = new PsiElement[]{originalStatement, conditionalExpression};
    final PsiExpression condition = (PsiExpression)conditionalExpression.getCondition().copy();
    final PsiElement[] thenElements = PsiTreeUtil.copyElements(originalElements);
    final PsiElement[] elseElements = PsiTreeUtil.copyElements(originalElements);
    thenElements[1].replace(conditionalExpression.getThenExpression());
    elseElements[1].replace(conditionalExpression.getElseExpression());

    PsiIfStatement statement = (PsiIfStatement)factory.createStatementFromText("if (true) { a = b } else { c = d }",
                                                                               null);
    statement = (PsiIfStatement)CodeStyleManager.getInstance(project).reformat(statement);
    statement.getCondition().replace(condition);
    statement = (PsiIfStatement)originalStatement.replace(statement);

    ((PsiBlockStatement)statement.getThenBranch()).getCodeBlock().getStatements()[0].replace(thenElements[0]);
    ((PsiBlockStatement)statement.getElseBranch()).getCodeBlock().getStatements()[0].replace(elseElements[0]);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
