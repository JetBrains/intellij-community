// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class DelegatingCallInfo<T extends GroovyPsiElement> implements CallInfo<T> {
  private final CallInfo<? extends T> myDelegate;

  public DelegatingCallInfo(CallInfo<? extends T> delegate) {
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

  @NotNull
  @Override
  public PsiElement getElementToHighlight() {
    return myDelegate.getElementToHighlight();
  }

  @NotNull
  @Override
  public T getCall() {
    return myDelegate.getCall();
  }
}
