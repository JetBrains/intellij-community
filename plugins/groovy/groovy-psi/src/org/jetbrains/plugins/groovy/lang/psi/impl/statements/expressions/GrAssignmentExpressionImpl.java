// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.ASSIGNMENT_OPERATORS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.getLeastUpperBoundNullable;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrOperatorExpressionImpl implements GrAssignmentExpression {

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  @Override
  @NotNull
  public GrExpression getLValue() {
    return Objects.requireNonNull(findExpressionChild(this));
  }

  @Override
  @Nullable
  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  @NotNull
  @Override
  public PsiElement getOperationToken() {
    return findNotNullChildByType(ASSIGNMENT_OPERATORS);
  }

  @Nullable
  @Override
  public IElementType getOperator() {
    return TokenSets.ASSIGNMENTS_TO_OPERATORS.get(getOperationTokenType());
  }

  @Override
  public boolean isOperatorAssignment() {
    return getOperationTokenType() != GroovyTokenTypes.mASSIGN;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @Override
  public PsiReference getReference() {
    return isOperatorAssignment() ? this : null;
  }

  @Nullable
  @Override
  public PsiType getLeftType() {
    return getLValue().getType();
  }

  @Nullable
  @Override
  public PsiType getRightType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @Nullable
  @Override
  public PsiType getType() {
    IElementType type = getOperationTokenType();
    if (TokenSets.ASSIGNMENTS_TO_OPERATORS.containsKey(type)) {
      return super.getType();
    }
    else if (type == GroovyElementTypes.T_ELVIS_ASSIGN) {
      return TypeInferenceHelper.getCurrentContext().getExpressionType(this, ELVIS_TYPE_CALCULATOR);
    }
    else {
      return getRightType();
    }
  }

  private final Function<GrAssignmentExpression, PsiType> ELVIS_TYPE_CALCULATOR =
    e -> getLeastUpperBoundNullable(e.getLeftType(), e.getRightType(), e.getManager());
}
