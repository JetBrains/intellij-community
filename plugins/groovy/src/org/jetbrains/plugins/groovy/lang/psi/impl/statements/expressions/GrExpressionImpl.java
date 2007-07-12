/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiType;

/**
 * @author ilyas
 */
public abstract class GrExpressionImpl extends GroovyPsiElementImpl implements GrExpression {
  public GrExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitExpression(this);
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr) throws IncorrectOperationException {
    return PsiImplUtil.replaceExpression(this, newExpr);
  }

  public PsiType getTypeByFQName(String fqName) {
    return getManager().getElementFactory().createTypeByFQClassName(fqName, getResolveScope());
  }
}
