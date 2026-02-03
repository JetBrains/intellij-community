// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * Represents the for-in clause in a for statement,
 * e.g.
 * <pre>{@code
 * for (int index, double value in [1.0, 2.0, 3.0]) {
 * }
 * }</pre>
 */

public interface GrForInClause extends GrForClause {

  @Override
  default GrVariable @NotNull [] getDeclaredVariables() {
    GrVariable variable = getDeclaredVariable();
    return variable == null ? GrVariable.EMPTY_ARRAY : new GrVariable[]{variable};
  }

  /**
   * @return the variable corresponding to the index inside the for clause. It is {@code index} variable from the example for {@link GrForInClause}.
   */
  @Nullable
  GrVariable getIndexVariable();

  /**
   * @return the variable corresponding to the value inside the for clause. It is {@code value} variable from the example for {@link GrForInClause}.
   */
  @Nullable
  GrVariable getDeclaredVariable();

  @Nullable
  GrExpression getIteratedExpression();

  @Nullable
  PsiElement getDelimiter();
}
