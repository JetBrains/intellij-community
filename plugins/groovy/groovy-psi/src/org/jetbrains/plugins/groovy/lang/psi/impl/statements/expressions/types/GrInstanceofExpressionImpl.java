// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiInstanceOfExpressionImpl;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrPatternVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_INSTANCEOF;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_NOT_INSTANCEOF;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessLocals;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessPatternVariables;

public class GrInstanceofExpressionImpl extends GrExpressionImpl implements GrInstanceOfExpression {

  private static final TokenSet INSTANCEOF_TOKENS = TokenSet.create(KW_INSTANCEOF, T_NOT_INSTANCEOF);

  public GrInstanceofExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitInstanceofExpression(this);
  }

  @Override
  public String toString() {
    return "Instanceof expression";
  }

  @Override
  public PsiType getType() {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_BOOLEAN);
  }

  @Override
  public @Nullable GrTypeElement getTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessLocals(processor) || !shouldProcessPatternVariables(state)) return true;
    return PsiInstanceOfExpressionImpl.processDeclarationsWithPattern(processor, state, lastParent, place, this::getPatternVariable);
  }

  @Override
  public @NotNull PsiElement getOperationToken() {
    return findNotNullChildByType(INSTANCEOF_TOKENS);
  }

  @Override
  public @NotNull GrExpression getOperand() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public @Nullable GrPatternVariable getPatternVariable() {
    return findChildByClass(GrPatternVariable.class);
  }
}
