package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical;

import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;

/**
 * author ven
 */
public class GrLogicalExpressionImpl extends GrBinaryExpressionImpl {
  public GrLogicalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiType getType() {
    return getTypeByFQName("java.lang.Boolean");
  }
}
