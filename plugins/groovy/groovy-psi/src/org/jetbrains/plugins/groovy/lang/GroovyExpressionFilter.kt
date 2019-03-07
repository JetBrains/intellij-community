// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

@Experimental
interface GroovyExpressionFilter {

  /**
   * @return `true` if [expression] is not really an expression,
   * meaning it would be transformed into something else
   */
  fun isFake(expression: GrExpression): Boolean
}
