// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {

  private static final Function<GrUnaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrUnaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrUnaryExpressionImpl unary) {
      final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(unary.multiResolve(false));

      if (isIncDecNumber(resolveResult)) {
        return unary.getOperand().getType();
      }

      final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(resolveResult, unary, PsiType.EMPTY_ARRAY);
      if (substituted != null) {
        return substituted;
      }

      GrExpression operand = unary.getOperand();
      if (operand == null) return null;

      final PsiType type = operand.getType();
      if (TypesUtil.isNumericType(type)) {
        return type;
      }

      return null;
    }

    //hack for DGM.next(Number):Number
    private boolean isIncDecNumber(GroovyResolveResult result) {
      PsiElement element = result.getElement();

      if (!(element instanceof PsiMethod)) return false;

      final PsiMethod method = element instanceof GrGdkMethod ? ((GrGdkMethod)element).getStaticMethod() : (PsiMethod)element;

      final String name = method.getName();
      if (!"next".equals(name) && !"previous".equals(name)) return false;

      if (!PsiUtil.isDGMMethod(method)) return false;

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1) return false;

      if (!parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_NUMBER)) return false;

      return true;
    }
  };

  private static final ResolveCache.PolyVariantResolver<GrUnaryExpressionImpl> OUR_RESOLVER = new ResolveCache.PolyVariantResolver<GrUnaryExpressionImpl>() {
    @NotNull
    @Override
    public GroovyResolveResult[] resolve(@NotNull GrUnaryExpressionImpl unary, boolean incompleteCode) {
      final GrExpression operand = unary.getOperand();
      if (operand == null) return GroovyResolveResult.EMPTY_ARRAY;

      final PsiType type = operand.getType();
      if (type == null) return GroovyResolveResult.EMPTY_ARRAY;

      return TypesUtil.getOverloadedUnaryOperatorCandidates(type, unary.getOperationTokenType(), operand, PsiType.EMPTY_ARRAY);
    }
  };

  public GrUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Unary expression";
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Override
  @NotNull
  public IElementType getOperationTokenType() {
    PsiElement opElement = getOperationToken();
    ASTNode node = opElement.getNode();
    assert node != null;
    return node.getElementType();
  }

  @Override
  @NotNull
  public PsiElement getOperationToken() {
    PsiElement opElement = findChildByType(TokenSets.UNARY_OP_SET);
    assert opElement != null;
    return opElement;
  }

  @Override
  public GrExpression getOperand() {
    return findExpressionChild(this);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitUnaryExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, OUR_RESOLVER);
  }

  @Override
  public boolean isPostfix() {
    return getFirstChild() instanceof GrExpression;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    final PsiElement opToken = getOperationToken();
    final int offset = opToken.getStartOffsetInParent();
    return new TextRange(offset, offset + opToken.getTextLength());
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
