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

public interface CallInfo<Call extends GroovyPsiElement> {
  @Nullable
  GrArgumentList getArgumentList();

  @Nullable
  PsiType[] getArgumentTypes();

  @Nullable
  GrExpression getInvokedExpression();

  @Nullable
  PsiType getQualifierInstanceType();

  @NotNull
  PsiElement getElementToHighlight();

  @NotNull
  GroovyResolveResult advancedResolve();

  @NotNull
  GroovyResolveResult[] multiResolve();

  @NotNull
  Call getCall();

  @NotNull
  GrExpression[] getExpressionArguments();

  @NotNull
  GrClosableBlock[] getClosureArguments();

  @NotNull
  GrNamedArgument[] getNamedArguments();
}
