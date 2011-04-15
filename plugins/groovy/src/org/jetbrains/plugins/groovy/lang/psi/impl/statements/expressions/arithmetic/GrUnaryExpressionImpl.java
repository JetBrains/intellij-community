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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {

  private static final Function<GrUnaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrUnaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrUnaryExpressionImpl unary) {
      GrExpression operand = unary.getOperand();
      if (operand == null) return null;

      PsiType opType = operand.getType();
      if (opType == null) return null;

      final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(unary.multiResolve(false));
      final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(resolveResult);
      if (substituted != null) {
        return TypesUtil.boxPrimitiveType(substituted, unary.getManager(), unary.getResolveScope());
      }

      IElementType opToken = unary.getOperationTokenType();
      if (opToken == GroovyTokenTypes.mBNOT && opType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return unary.getTypeByFQName(GroovyCommonClassNames.JAVA_UTIL_REGEX_PATTERN);
      }

      return opType;
    }
  };

  private static final ResolveCache.PolyVariantResolver<GrUnaryExpressionImpl> OUR_RESOLVER =
    new ResolveCache.PolyVariantResolver<GrUnaryExpressionImpl>() {
      @Override
      public GroovyResolveResult[] resolve(GrUnaryExpressionImpl unary, boolean incompleteCode) {
        final GrExpression operand = unary.getOperand();
        if (operand == null) return GroovyResolveResult.EMPTY_ARRAY;

        final PsiType type = operand.getType();
        if (type == null) return GroovyResolveResult.EMPTY_ARRAY;

        return TypesUtil.getOverloadedOperatorCandidates(type, unary.getOperationTokenType(), unary, PsiType.EMPTY_ARRAY);
      }
    };

  public GrUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Unary expression";
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPE_CALCULATOR);
  }

  public IElementType getOperationTokenType() {
    PsiElement opElement = getOperationToken();
    ASTNode node = opElement.getNode();
    assert node != null;
    return node.getElementType();
  }

  public PsiElement getOperationToken() {
    PsiElement opElement = findChildByType(TokenSets.UNARY_OP_SET);
    assert opElement != null;
    return opElement;
  }

  public GrExpression getOperand() {
    return findChildByClass(GrExpression.class);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitUnaryExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return (GroovyResolveResult[])getManager().getResolveCache().resolveWithCaching(this, OUR_RESOLVER, false, incompleteCode);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement opToken = getOperationToken();
    final int offset = opToken.getStartOffsetInParent();
    return new TextRange(offset, offset + opToken.getTextLength());
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
    throw new IncorrectOperationException("unary expression cannot be renamed to anything");
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("unary expression cannot be bounded to anything");
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