/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrExpressionImpl implements GrBinaryExpression {

  private static final ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl> RESOLVER =
    new ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl>() {
      @Override
      public GroovyResolveResult[] resolve(GrBinaryExpressionImpl binary, boolean incompleteCode) {
        final IElementType opType = binary.getOperationTokenType();
        if (opType == null) return GroovyResolveResult.EMPTY_ARRAY;

        final PsiType lType = binary.getLeftOperand().getType();
        if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;

        final GrExpression rightOperand = binary.getRightOperand();
        PsiType rType = rightOperand == null ? null : rightOperand.getType();
        return TypesUtil.getOverloadedOperatorCandidates(lType, opType, binary, new PsiType[]{rType});
      }
    };

  private static final Function<GrBinaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrBinaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrBinaryExpressionImpl binary) {
      final Function<GrBinaryExpressionImpl, PsiType> typeCalculator = binary.getTypeCalculator();
      if (typeCalculator != null) {
        final PsiType result = typeCalculator.fun(binary);
        if (result != null) return result;
      }

      final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(binary.multiResolve(false));
      final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(resolveResult);
      return TypesUtil.boxPrimitiveType(substituted, binary.getManager(), binary.getResolveScope());
    }
  };

  public GrBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public GrExpression getLeftOperand() {
    GrExpression result = findChildByClass(GrExpression.class);
    assert result != null;
    return result;
  }

  public GrExpression getRightOperand() {
    GrExpression left = getLeftOperand();
    PsiElement run = left.getNextSibling();
    while (run != null) {
      if (run instanceof GrExpression) return (GrExpression) run;
      run = run.getNextSibling();
    }
    return null;
  }

  @Nullable
  public IElementType getOperationTokenType() {
    final PsiElement child = getOperationToken();
    if (child == null) return null;
    final ASTNode node = child.getNode();
    assert node != null;
    return node.getElementType();
  }

  @Nullable
  public PsiElement getOperationToken() {
    return findChildByType(TokenSets.BINARY_OP_SET);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitBinaryExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return (GroovyResolveResult[])getManager().getResolveCache().resolveWithCaching(this, RESOLVER, false, incompleteCode);
  }

  @Override
  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPE_CALCULATOR);
  }

  @Nullable
  protected Function<GrBinaryExpressionImpl,PsiType> getTypeCalculator() {
    return null;
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement token = getOperationToken();
    assert token != null;
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
    final PsiElement token = getOperationToken();
    if (token != null) {
      return this;
    }
    return null;
  }
}
