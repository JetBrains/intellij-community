// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
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
import org.jetbrains.plugins.groovy.lang.resolve.references.GrUnaryOperatorReference;

public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {

  private static final Function<GrUnaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrUnaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType fun(GrUnaryExpressionImpl unary) {
      final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(unary.getReference().multiResolve(false));

      if (isIncDecNumber(resolveResult)) {
        return ObjectUtils.doIfNotNull(unary.getOperand(), GrExpression::getType);
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

  private final GroovyReference myReference = new GrUnaryOperatorReference(this);

  public GrUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public GroovyReference getReference() {
    return myReference;
  }

  @Override
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

  @Override
  public boolean isPostfix() {
    return getFirstChild() instanceof GrExpression;
  }
}
