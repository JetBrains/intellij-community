// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import org.jetbrains.plugins.groovy.lang.GroovyElementFilter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class SpockElementFilter : GroovyElementFilter {

  override fun isFake(element: GroovyPsiElement): Boolean {
    return element is GrExpression
           && (element.isInteractionPart() || element.isTableColumnSeparator())
           && element.isInsideSpecification()
  }
}
