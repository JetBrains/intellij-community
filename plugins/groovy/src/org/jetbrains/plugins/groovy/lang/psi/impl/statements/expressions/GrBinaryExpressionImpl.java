/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrExpressionImpl implements GrBinaryExpression {

  private static final ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl> RESOLVER =
    new ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl>() {
      @NotNull
      @Override
      public GroovyResolveResult[] resolve(@NotNull GrBinaryExpressionImpl binary, boolean incompleteCode) {
        final IElementType opType = binary.getOperationTokenType();

        final GrExpression left = binary.getLeftOperand();
        final PsiType lType = left.getType();
        if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;

        PsiType rType = getRightType(binary);
        return TypesUtil.getOverloadedOperatorCandidates(lType, opType, binary, new PsiType[]{rType}, incompleteCode);
      }
    };

  @Nullable
  private static PsiType getRightType(GrBinaryExpressionImpl binary) {
    final GrExpression rightOperand = binary.getRightOperand();
    return rightOperand == null ? null : rightOperand.getType();
  }

  private static final Function<GrBinaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      return binary.calcType();
    }
  };

  public GrBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public GrExpression getLeftOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Nullable
  public PsiType getLeftOperandType() {
    GrExpression leftOperand = getLeftOperand();
    return leftOperand instanceof GrBinaryExpressionImpl ? ((GrBinaryExpressionImpl)leftOperand).calcType() : leftOperand.getType();
  }

  @Nullable
  public PsiType getRightOperandType() {
    GrExpression rightOperand = getRightOperand();
    if (rightOperand == null) return null;
    if (rightOperand instanceof GrBinaryExpressionImpl) return ((GrBinaryExpressionImpl)rightOperand).calcType();
    return rightOperand.getType();
  }

  @Nullable
  public GrExpression getRightOperand() {
    final PsiElement last = getLastChild();
    return last instanceof GrExpression ? (GrExpression)last : null;
  }

  @NotNull
  public IElementType getOperationTokenType() {
    final PsiElement child = getOperationToken();
    final ASTNode node = child.getNode();
    assert node != null;
    return node.getElementType();
  }

  @NotNull
  public PsiElement getOperationToken() {
    return findNotNullChildByType(TokenSets.BINARY_OP_SET);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Nullable
  protected Function<GrBinaryExpressionImpl,PsiType> getTypeCalculator() {
    return null;
  }

  protected PsiType calcType() {
    final Function<GrBinaryExpressionImpl, PsiType> typeCalculator = getTypeCalculator();
    if (typeCalculator != null) {
      final PsiType result = typeCalculator.fun(this);
      if (result != null) return result;
    }

    final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(multiResolve(false));
    final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(resolveResult, this, new PsiType[]{getRightType(this)});
    return TypesUtil.boxPrimitiveType(substituted, getManager(), getResolveScope());
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement token = getOperationToken();
    final int offset = token.getStartOffsetInParent();
    return new TextRange(offset, offset + token.getTextLength());
  }

  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("binary expression cannot be renamed");
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("binary expression cannot be bound to anything");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

}
