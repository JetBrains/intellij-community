// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import org.jetbrains.plugins.groovy.lang.GroovyExpressionFilter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class SpockExpressionFilter : GroovyExpressionFilter {

  override fun isFake(expression: GrExpression): Boolean {
    return (expression.isInteractionPart() || expression.isTableColumnSeparator()) && expression.isInsideSpecification()
  }
}
