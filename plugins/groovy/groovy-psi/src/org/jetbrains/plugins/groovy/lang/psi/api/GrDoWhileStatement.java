// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface GrDoWhileStatement extends GrLoopStatement {

  @NotNull
  PsiElement getDoKeyword();

  @Override
  @Nullable
  GrStatement getBody();

  @Nullable
  PsiElement getLParenth();

  @Nullable
  GrExpression getCondition();

  @Nullable
  PsiElement getRParenth();
}
