/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;

/**
 * @author ilyas
 */
public class GrSuperReferenceExpressionImpl extends GrExpressionImpl implements GrSuperReferenceExpression {
  public GrSuperReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "'super'reference expression";
  }

  public PsiType getType() {
    return null;
  }
}
