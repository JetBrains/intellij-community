// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrUnaryOperatorReference;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessPatternVariables;
import static org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculatorKt.getTypeFromResult;

public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {

  private final GroovyMethodCallReference myReference = new GrUnaryOperatorReference(this);

  public GrUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull GroovyMethodCallReference getReference() {
    return myReference;
  }

  @Override
  public String toString() {
    return "Unary expression";
  }

  @Override
  public @Nullable PsiType getOperationType() {
    final GroovyCallReference reference = getReference();
    final GroovyResolveResult result = reference.advancedResolve();
    final PsiType operatorType = getTypeFromResult(result, reference.getArguments(), this);
    if (operatorType != null) {
      return operatorType;
    }

    final GrExpression operand = getOperand();
    if (operand == null) {
      return null;
    }
    final PsiType operandType = operand.getType();
    if (TypesUtil.isNumericType(operandType)) {
      return operandType;
    }

    return null;
  }

  @Override
  public @NotNull IElementType getOperationTokenType() {
    PsiElement opElement = getOperationToken();
    ASTNode node = opElement.getNode();
    assert node != null;
    return node.getElementType();
  }

  @Override
  public @NotNull PsiElement getOperationToken() {
    PsiElement opElement = findChildByType(TokenSets.UNARY_OP_SET);
    assert opElement != null;
    return opElement;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor) || !shouldProcessPatternVariables(state)) return true;
    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }

  @Override
  public GrExpression getOperand() {
    return findExpressionChild(this);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitUnaryExpression(this);
  }

  @Override
  public boolean isPostfix() {
    return getFirstChild() instanceof GrExpression;
  }
}
