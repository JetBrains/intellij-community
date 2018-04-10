// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class DelegatingCallInfo<T extends GroovyPsiElement> implements CallInfo<T> {
  private final CallInfo<T> myDelegate;

  public DelegatingCallInfo(CallInfo<T> delegate) {
    myDelegate = delegate;
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return myDelegate.getArgumentList();
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    return myDelegate.getArgumentTypes();
  }

  @Nullable
  @Override
  public GrExpression getInvokedExpression() {
    return myDelegate.getInvokedExpression();
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return myDelegate.getQualifierInstanceType();
  }

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    return myDelegate.getElementToHighlight();
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return myDelegate.advancedResolve();
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve() {
    return myDelegate.multiResolve();
  }

  @NotNull
  @Override
  public T getCall() {
    return myDelegate.getCall();
  }

  @NotNull
  @Override
  public GrExpression[] getExpressionArguments() {
    return myDelegate.getExpressionArguments();
  }

  @NotNull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return myDelegate.getClosureArguments();
  }

  @NotNull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    return myDelegate.getNamedArguments();
  }
}
