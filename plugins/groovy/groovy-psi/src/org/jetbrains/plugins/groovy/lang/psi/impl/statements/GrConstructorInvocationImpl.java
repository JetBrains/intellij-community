// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrCallImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrConstructorInvocationReference;

public class GrConstructorInvocationImpl extends GrCallImpl implements GrConstructorInvocation {

  private final GroovyConstructorReference myConstructorReference = new GrConstructorInvocationReference(this);

  public GrConstructorInvocationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitConstructorInvocation(this);
  }

  @Override
  public String toString() {
    return "Constructor invocation";
  }

  @Override
  public @NotNull GroovyConstructorReference getConstructorReference() {
    return myConstructorReference;
  }

  @Override
  public boolean isSuperCall() {
    return getKeywordType() == GroovyTokenTypes.kSUPER;
  }

  @Override
  public boolean isThisCall() {
    return getKeywordType() == GroovyTokenTypes.kTHIS;
  }

  private @Nullable IElementType getKeywordType() {
    GrReferenceExpression keyword = getInvokedExpression();
    PsiElement refElement = keyword.getReferenceNameElement();
    if (refElement == null) return null;

    return refElement.getNode().getElementType();
  }

  @Override
  public @NotNull GrReferenceExpression getInvokedExpression() {
    return findNotNullChildByClass(GrReferenceExpression.class);
  }

  @Override
  public GroovyResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return myConstructorReference.multiResolve(incompleteCode);
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    PsiClass aClass = getDelegatedClass();
    if (aClass == null) return GroovyResolveResult.EMPTY_ARRAY;

    return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, this, null, PsiSubstitutor.EMPTY, true, true)};
  }

  @Override
  public @Nullable PsiClass getDelegatedClass() {
    PsiClass typeDefinition = PsiUtil.getContextClass(this);
    if (typeDefinition != null) {
      return isThisCall() ? typeDefinition : typeDefinition.getSuperClass();
    }
    return null;
  }

  @Override
  public GroovyResolveResult @NotNull [] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolve(true);
  }

  @Override
  public @NotNull GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }
}
