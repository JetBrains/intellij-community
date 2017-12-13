/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrOperatorExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessDynamicProperties;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrOperatorExpressionImpl implements GrAssignmentExpression {

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

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
    return findNotNullChildByType(TokenSets.ASSIGNMENTS);
  }

  @Nullable
  @Override
  public IElementType getOperator() {
    return TokenSets.ASSIGNMENTS_TO_OPERATORS.get(getOperationTokenType());
  }

  @Override
  public boolean isOperatorAssignment() {
    return getOperationTokenType() != GroovyTokenTypes.mASSIGN;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @Override
  public PsiReference getReference() {
    return isOperatorAssignment() ? this : null;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessBindings(this, processor, lastParent, place)) return true;
    return processLValue(processor, state, place, (GroovyFileImpl)getParent(), getLValue());
  }

  static boolean shouldProcessBindings(@NotNull PsiElement owner,
                                       @NotNull PsiScopeProcessor processor,
                                       @Nullable PsiElement lastParent,
                                       @NotNull PsiElement place) {
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (!ResolveUtil.shouldProcessProperties(classHint)) return false;
    if (!shouldProcessDynamicProperties(processor)) return false;

    PsiElement parent = owner.getParent();
    if (!(parent instanceof GroovyFileImpl)) return false;

    final GroovyFileImpl file = (GroovyFileImpl)parent;
    if (!file.isInScriptBody(lastParent, place)) return false;

    return true;
  }


  static boolean processLValue(@NotNull PsiScopeProcessor processor,
                               @NotNull ResolveState state,
                               @NotNull PsiElement place,
                               @NotNull GroovyFileImpl file,
                               @NotNull GrExpression lValue) {
    if (!(lValue instanceof GrReferenceExpression)) return true;

    final GrReferenceExpression lReference = (GrReferenceExpression)lValue;
    if (lReference.isQualified()) return true;

    final String name = lReference.getReferenceName();
    if (name == null) return true;

    String hintName = ResolveUtil.getNameHint(processor);
    if (hintName != null && !name.equals(hintName)) return true;

    if (lReference != place && lReference.resolve() != null && !(lReference.resolve() instanceof GrBindingVariable)) return true;
    final ConcurrentMap<String, GrBindingVariable> bindings = file.getBindings();
    GrBindingVariable variable = bindings.get(name);
    if (variable == null) {
      variable = ConcurrencyUtil.cacheOrGet(bindings, name, new GrBindingVariable(file, name, true));
    }

    if (!variable.hasWriteAccess()) return true;
    return processor.execute(variable, state);
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
    if (TokenSets.ASSIGNMENTS_TO_OPERATORS.containsKey(getOperationTokenType())) {
      return super.getType();
    }
    else {
      return getRightType();
    }
  }
}
