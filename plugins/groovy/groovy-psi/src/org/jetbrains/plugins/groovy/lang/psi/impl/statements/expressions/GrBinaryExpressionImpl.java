/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
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

import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrOperatorExpressionImpl implements GrBinaryExpression {

  private static final ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl> RESOLVER = new DependentResolver<GrBinaryExpressionImpl>() {

    @Override
    public Collection<PsiPolyVariantReference> collectDependencies(@NotNull GrBinaryExpressionImpl expression) {
      // to avoid SOE, resolve all binary sub-expressions starting from the innermost
      final List<PsiPolyVariantReference> subExpressions = new SmartList<>();
      expression.getLeftOperand().accept(new PsiRecursiveElementWalkingVisitor() {
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
            subExpressions.add((GrBinaryExpressionImpl)element);
          }
        }
      });
      return subExpressions;
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
