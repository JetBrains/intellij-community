// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class GrBinaryExprInfo implements CallInfo<GrBinaryExpression> {
  private final GrBinaryExpression myExpr;

  public GrBinaryExprInfo(GrBinaryExpression expr) {
    myExpr = expr;
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return null;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    GrExpression operand = myExpr.getRightOperand();

    return new PsiType[]{operand != null ? operand.getType() : null};
  }

  @Nullable
  @Override
  public GrExpression getInvokedExpression() {
    return myExpr.getLeftOperand();
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return myExpr.getLeftOperand().getType();
  }

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    return myExpr.getOperationToken();
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve());
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve() {
    return myExpr.multiResolve(false);
  }

  @NotNull
  @Override
  public GrBinaryExpression getCall() {
    return myExpr;
  }

  @NotNull
  @Override
  public GrExpression[] getExpressionArguments() {
    GrExpression right = myExpr.getRightOperand();
    if (right != null) {
      return new GrExpression[]{right};
    }
    else {
      return GrExpression.EMPTY_ARRAY;
    }
  }

  @NotNull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    return GrNamedArgument.EMPTY_ARRAY;
  }
}
