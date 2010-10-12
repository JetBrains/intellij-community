package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxReferenceExpressionImpl extends JavaFxReferenceElementImpl implements JavaFxReferenceExpression {
  public JavaFxReferenceExpressionImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public PsiType getType() {
    final PsiReference reference = getReference();
    if (reference != null) {
      return JavaFxTypeUtil.createType(reference.resolve());
    }
    return null;
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }
}
