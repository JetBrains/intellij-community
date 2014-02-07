/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * Created by Max Medvedev on 05/02/14
 */
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
  public PsiElement getHighlightElementForCategoryQualifier() throws UnsupportedOperationException {
    return myDelegate.getHighlightElementForCategoryQualifier();
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
