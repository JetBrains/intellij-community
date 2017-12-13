/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrOperatorExpressionImpl implements GrBinaryExpression {

  private static final ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl> RESOLVER = new DependentResolver<GrBinaryExpressionImpl>() {

    @Override
    protected void collectDependencies(@NotNull GrBinaryExpressionImpl ref, @NotNull Consumer<? super PsiPolyVariantReference> consumer) {
      // to avoid SOE, resolve all binary sub-expressions starting from the innermost
      ref.getLeftOperand().accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof GrBinaryExpression) {
            super.visitElement(element);
          }
          else if (element instanceof GrParenthesizedExpression) {
            GrExpression operand = ((GrParenthesizedExpression)element).getOperand();
            if (operand != null) super.visitElement(operand);
          }
        }

        @Override
        protected void elementFinished(PsiElement element) {
          if (element instanceof GrBinaryExpressionImpl) {
            consumer.consume((GrBinaryExpressionImpl)element);
          }
        }
      });
    }

    @NotNull
    @Override
    public ResolveResult[] doResolve(@NotNull GrBinaryExpressionImpl binary, boolean incompleteCode) {
      final IElementType opType = binary.getOperationTokenType();
      final PsiType lType = binary.getLeftType();
      if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;
      PsiType rType = binary.getRightType();
      return TypesUtil.getOverloadedOperatorCandidates(lType, opType, binary, new PsiType[]{rType}, incompleteCode);
    }
  };

  @Nullable
  public PsiType getRightType() {
    final GrExpression rightOperand = getRightOperand();
    return rightOperand == null ? null : rightOperand.getType();
  }

  @Nullable
  @Override
  public PsiType getLeftType() {
    return getLeftOperand().getType();
  }

  public GrBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public GrExpression getLeftOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  @Nullable
  public GrExpression getRightOperand() {
    final PsiElement last = getLastChild();
    return last instanceof GrExpression ? (GrExpression)last : null;
  }

  @Override
  @NotNull
  public PsiElement getOperationToken() {
    return findNotNullChildByType(TokenSets.BINARY_OP_SET);
  }

  @Nullable
  @Override
  public IElementType getOperator() {
    return getOperationTokenType();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiReference getReference() {
    return this;
  }
}
