/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ilyas
 */
public abstract class GrExpressionImpl extends GroovyPsiElementImpl implements GrExpression {
  public GrExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrStatement replace(@NotNull PsiElement newExpr) throws IncorrectOperationException {
    if (getParent() == null ||
        getParent().getNode() == null ||
        newExpr.getNode() == null ||
        !(newExpr instanceof GrStatement)) {
      throw new IncorrectOperationException();
    }
    ASTNode parentNode = getParent().getNode();
    ASTNode newNode = newExpr.getNode();
    parentNode.replaceChild(this.getNode(), newNode);
    if (!(newNode.getPsi() instanceof GrStatement)){
      throw new IncorrectOperationException();
    }
    return (GrStatement) newNode.getPsi();
  }

  




}
