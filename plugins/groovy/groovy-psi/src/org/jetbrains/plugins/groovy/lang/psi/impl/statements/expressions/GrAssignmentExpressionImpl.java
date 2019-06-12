// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrOperatorExpressionImpl implements GrAssignmentExpression {

  private final SafePublicationClearableLazyValue<GroovyCallReference> myReference = new SafePublicationClearableLazyValue<>(
    () -> isOperatorAssignment() ? new GrOperatorReference(this) : null
  );

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public GroovyCallReference getReference() {
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
    return findNotNullChildByType(ASSIGNMENTS);
  }

  @Nullable
  @Override
  public IElementType getOperator() {
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
