// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface CallInfo<Call extends GroovyPsiElement> {
  @Nullable
  GrArgumentList getArgumentList();

  PsiType @Nullable [] getArgumentTypes();

  @Nullable
  GrExpression getInvokedExpression();

  @NotNull
  PsiElement getElementToHighlight();

  @NotNull
  Call getCall();
}
