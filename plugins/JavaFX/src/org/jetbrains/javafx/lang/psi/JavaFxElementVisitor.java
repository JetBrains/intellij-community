package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiElementVisitor;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxElementVisitor extends PsiElementVisitor {
  public void visitClassDefinition(JavaFxClassDefinition node) {
    visitElement(node);
  }

  public void visitFunctionDefinition(JavaFxFunctionDefinition node) {
    visitElement(node);
  }

  public void visitVariableDeclaration(JavaFxVariableDeclaration node) {
    visitElement(node);
  }

  public void visitLiteralExpression(JavaFxLiteralExpression node) {
    visitExpression(node);
  }

  public void visitStringExpression(JavaFxStringExpression node) {
    visitLiteralExpression(node);
  }

  public void visitObjectLiteral(JavaFxObjectLiteral node) {
    visitLiteralExpression(node);
  }

  public void visitExpression(JavaFxExpression node) {
    visitElement(node);
  }

  public void visitBreakExpression(JavaFxBreakExpression node) {
    visitExpression(node);
  }

  public void visitContinueExpression(JavaFxContinueExpression node) {
    visitExpression(node);
  }

  public void visitReturnExpression(JavaFxReturnExpression node) {
    visitExpression(node);
  }

  public void visitAssignmentExpression(JavaFxAssignmentExpression node) {
    visitExpression(node);
  }

  public void visitModifierList(JavaFxModifierList node) {
    visitElement(node);
  }

  public void visitReferenceElement(JavaFxReferenceElement node) {
    visitElement(node);
  }

  public void visitReferenceExpression(JavaFxReferenceExpression node) {
    visitExpression(node);
  }

  public void visitThisExpression(JavaFxThisReferenceExpression node) {
    visitExpression(node);
  }
}
