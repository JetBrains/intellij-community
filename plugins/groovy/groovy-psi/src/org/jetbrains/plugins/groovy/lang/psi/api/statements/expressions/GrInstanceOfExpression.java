// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public interface GrInstanceOfExpression extends GrExpression {

  @Nullable
  GrTypeElement getTypeElement();

  @Nullable
  PsiElement getNegationToken();

  @NotNull
  GrExpression getOperand();
}
