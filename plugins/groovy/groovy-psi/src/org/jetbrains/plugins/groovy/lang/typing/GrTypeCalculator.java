// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.openapi.util.ClassExtension;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * This API provides ability to plug into expression type calculation in Groovy.
 * Each expression has its default implementation which is executed last.
 *
 * @param <T> expression class
 * @see DefaultListOrMapTypeCalculator
 * @see DefaultIndexAccessTypeCalculator
 */
public interface GrTypeCalculator<T extends GrExpression> {

  ClassExtension<GrTypeCalculator<?>> EP = new ClassExtension<>("org.intellij.groovy.typeCalculator");

  /**
   * @return {@code expression} type if some implementation can calculate it, otherwise {@code null}. <br/>
   * The result type is the first non-{@code null} value returned.
   */
  @SuppressWarnings("unchecked")
  static @Nullable PsiType getTypeFromCalculators(@NotNull GrExpression expression) {
    for (GrTypeCalculator<?> calculator : EP.forKey(expression.getClass())) {
      PsiType type = ((GrTypeCalculator<GrExpression>)calculator).getType(expression);
      if (type != null) return type;
    }
    return null;
  }

  /**
   * @return {@code null} if expression type cannot be calculated.
   */
  @Nullable
  PsiType getType(@NotNull T expression);
}
