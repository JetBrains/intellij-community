// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference;
import org.jetbrains.plugins.groovy.util.SafePublicationClearableLazyValue;

import java.util.Objects;
import java.util.function.Function;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.ASSIGNMENTS;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.getLeastUpperBoundNullable;

public class GrAssignmentExpressionImpl extends GrOperatorExpressionImpl implements GrAssignmentExpression {

  private final SafePublicationClearableLazyValue<GroovyCallReference> myReference = new SafePublicationClearableLazyValue<>(
    () -> isOperatorAssignment() ? new GrOperatorReference(this) : null
  );

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable GroovyCallReference getReference() {
    return myReference.getValue();
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myReference.clear();
  }

  @Override
  public String toString() {
    return "Assignment expression";
  }

  @Override
  public @NotNull GrExpression getLValue() {
    return Objects.requireNonNull(findExpressionChild(this));
  }

  @Override
  public @Nullable GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  @Override
  public @NotNull PsiElement getOperationToken() {
    return findNotNullChildByType(ASSIGNMENTS);
  }

  @Override
  public @Nullable IElementType getOperator() {
    return TokenSets.ASSIGNMENTS_TO_OPERATORS.get(getOperationTokenType());
  }

  @Override
  public boolean isOperatorAssignment() {
    return TokenSets.ASSIGNMENTS_TO_OPERATORS.containsKey(getOperationTokenType());
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @Override
  public @Nullable PsiType getLeftType() {
    return getLValue().getType();
  }

  @Override
  public @Nullable PsiType getRightType() {
    GrExpression rValue = getRValue();
    return rValue == null ? null : rValue.getType();
  }

  @Override
  public @Nullable PsiType getType() {
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
