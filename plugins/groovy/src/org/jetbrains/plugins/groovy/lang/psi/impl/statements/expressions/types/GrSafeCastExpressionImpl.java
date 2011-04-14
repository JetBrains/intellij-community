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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ven
 */
public class GrSafeCastExpressionImpl extends GrExpressionImpl implements GrSafeCastExpression, PsiPolyVariantReference {

  private static final Function<GrSafeCastExpressionImpl, PsiType> TYPE_CALCULATOR = new NullableFunction<GrSafeCastExpressionImpl, PsiType>() {
    @Override
    public PsiType fun(GrSafeCastExpressionImpl cast) {
      GrTypeElement typeElement = cast.getCastTypeElement();
      if (typeElement != null) return TypesUtil.boxPrimitiveType(typeElement.getType(), cast.getManager(), cast.getResolveScope());
      return null;
    }
  };

  private static final class OurResolver implements ResolveCache.PolyVariantResolver<GrSafeCastExpressionImpl> {
    @Override
    public ResolveResult[] resolve(GrSafeCastExpressionImpl cast, boolean incompleteCode) {
      PsiType type = cast.getOperand().getType();
      if (type == null) {
        return GroovyResolveResult.EMPTY_ARRAY;
      }

      final GrTypeElement typeElement = cast.getCastTypeElement();
      final PsiType toCast = typeElement == null ? null : typeElement.getType();
      return TypesUtil.getOverloadedOperatorCandidates(
        type,
        GroovyTokenTypes.kAS,
        cast,
        new PsiType[]{TypesUtil.createJavaLangClassType(toCast, cast.getProject(), cast.getResolveScope())}
      );
    }
  }

  private static final OurResolver OUR_RESOLVER = new OurResolver();

  public GrSafeCastExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitSafeCastExpression(this);
  }

  public String toString() {
    return "Safe cast expression";
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPE_CALCULATOR);
  }

  @Nullable
  public GrTypeElement getCastTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @NotNull
  public GrExpression getOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement as = findNotNullChildByType(GroovyTokenTypes.kAS);
    final int offset = as.getStartOffsetInParent();
    return new TextRange(offset, offset + 2);
  }

  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false)).getElement();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new UnsupportedOperationException("safe cast cannot be renamed");
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException("safe cast can be bounded to nothing");
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

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return (GroovyResolveResult[])getManager().getResolveCache().resolveWithCaching(this, OUR_RESOLVER, false, incompleteCode);
  }
}