// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface GrForInClause extends GrForClause {

  @NotNull
  @Override
  default GrVariable[] getDeclaredVariables() {
    GrVariable variable = getDeclaredVariable();
    return variable == null ? GrVariable.EMPTY_ARRAY : new GrVariable[]{variable};
  }

  @Nullable
  GrVariable getDeclaredVariable();

  @Nullable
  GrExpression getIteratedExpression();

  @NotNull
  PsiElement getDelimiter();
}
