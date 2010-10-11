package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxAssignmentExpression;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxAssignmentExpressionImpl extends JavaFxBaseElementImpl implements JavaFxAssignmentExpression {
  public JavaFxAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  public JavaFxExpression getAssignedValue() {
    PsiElement child = getLastChild();
    while (child != null && !(child instanceof JavaFxExpression)) {
      if (child instanceof PsiErrorElement) {
        return null; // incomplete assignment operator can't be analyzed properly, bail out.
      }
      child = child.getPrevSibling();
    }
    return (JavaFxExpression)child;
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression assignedValue = getAssignedValue();
    if (assignedValue == null) {
      return null;
    }
    return assignedValue.getType();
  }
}
