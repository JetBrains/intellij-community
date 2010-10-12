package org.jetbrains.javafx.codeInsight.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.javafx.JavaFxLanguage;
import org.jetbrains.javafx.lang.psi.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxGotoBreakContinueHandler implements GotoDeclarationHandler {
  public PsiElement getGotoDeclarationTarget(PsiElement sourceElement) {
    if (sourceElement == null || !(sourceElement.getLanguage() instanceof JavaFxLanguage)) {
      return null;
    }
    final JavaFxBreakExpression breakExpression = PsiTreeUtil.getParentOfType(sourceElement, JavaFxBreakExpression.class, false);
    if (breakExpression != null) {
      final JavaFxLoopExpression parentExpression = PsiTreeUtil.getParentOfType(breakExpression, JavaFxLoopExpression.class);
      if (parentExpression == null) {
        return null;
      }
      PsiElement nextSibling = PsiTreeUtil.getNextSiblingOfType(parentExpression, JavaFxExpression.class);
      if (nextSibling != null) {
        return nextSibling;
      }
      nextSibling = parentExpression.getNextSibling();
      if (nextSibling != null) {
        return nextSibling;
      }

      final JavaFxVariableDeclaration variableDeclaration = PsiTreeUtil.getParentOfType(parentExpression, JavaFxVariableDeclaration.class);
      if (variableDeclaration != null) {
        nextSibling = PsiTreeUtil.getNextSiblingOfType(variableDeclaration, JavaFxExpression.class);
        if (nextSibling != null) {
          return nextSibling;
        }
      }

      final JavaFxExpression body = parentExpression.getBody();
      assert body != null;
      return body.getLastChild();
    }
    final JavaFxContinueExpression continueExpression = PsiTreeUtil.getParentOfType(sourceElement, JavaFxContinueExpression.class, false);
    if (continueExpression != null) {
      return PsiTreeUtil.getParentOfType(continueExpression, JavaFxLoopExpression.class);
    }
    return null;
  }
}
