/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.openapi.util.ClassExtension;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * This API provides ability to plug into expression type calculation in Groovy.
 * Each expression has its default implementation which is executed last.
 * <p>
 * The API is experimental at least until 2017.3.
 *
 * @param <T> expression class
 * @see DefaultListOrMapTypeCalculator
 * @see DefaultIndexAccessTypeCalculator
 */
@Experimental
public interface GrTypeCalculator<T extends GrExpression> {

  ClassExtension<GrTypeCalculator> EP = new ClassExtension<>("org.intellij.groovy.typeCalculator");

  /**
   * @return {@code expression} type if some implementation can calculate it, otherwise {@code null}. <br/>
   * The result type is the first non-{@code null} value returned.
   */
  @Nullable
  static PsiType getTypeFromCalculators(@NotNull GrExpression expression) {
    for (GrTypeCalculator calculator : EP.forKey(expression.getClass())) {
      //noinspection unchecked
      PsiType type = calculator.getType(expression);
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
