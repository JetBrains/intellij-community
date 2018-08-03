// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public class GrForInClauseImpl extends GroovyPsiElementImpl implements GrForInClause, GrParameterListOwner {

  public GrForInClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitForInClause(this);
  }

  public String toString() {
    return "In clause";
  }

  @Override
  public GrParameter getDeclaredVariable() {
    return findChildByClass(GrParameter.class);
  }

  @NotNull
  @Override
  public GrParameter[] getParameters() {
    final GrParameter declaredVariable = getDeclaredVariable();
    return declaredVariable == null ? GrParameter.EMPTY_ARRAY : new GrParameter[]{declaredVariable};
  }

  @Override
  public GrParameterList getParameterList() {
    return null;
  }

  @Override
  public boolean isVarArgs() {
    throw new IncorrectOperationException("For in clause cannot have varargs");
  }

  @Override
  @Nullable
  public GrExpression getIteratedExpression() {
    return findExpressionChild(this);
  }

  @NotNull
  @Override
  public PsiElement getDelimiter() {
    PsiElement in = findChildByType(GroovyTokenTypes.kIN);
    if (in != null) return in;

    PsiElement colon = findChildByType(GroovyTokenTypes.mCOLON);
    return ObjectUtils.assertNotNull(colon);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null) return true;
    GrParameter variable = getDeclaredVariable();
    if (variable == null) return true;
    return ResolveUtil.processElement(processor, variable, state);
  }
}
