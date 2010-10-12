package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxThisReferenceExpression;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxThisReference;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxThisReferenceExpressionImpl extends JavaFxReferenceExpressionImpl implements JavaFxThisReferenceExpression {
  public JavaFxThisReferenceExpressionImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxClassDefinition getContainingClass() {
    return PsiTreeUtil.getParentOfType(this, JavaFxClassDefinition.class);
  }

  @Override
  public PsiReference getReference() {
    return new JavaFxThisReference(this);
  }

  @Override
  public PsiType getType() {
    final PsiElement containingElement = getReference().resolve();
    if (containingElement == null) {
      return null;
    }
    return JavaFxTypeUtil.createType(containingElement);
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitThisExpression(this);
  }
}
