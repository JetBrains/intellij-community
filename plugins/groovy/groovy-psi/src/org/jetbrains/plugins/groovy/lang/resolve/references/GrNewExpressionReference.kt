// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

class GrNewExpressionReference(element: GrNewExpression) : GrConstructorReference<GrNewExpression>(element) {

  override fun resolveClass(): GroovyResolveResult? = element.referenceElement?.advancedResolve()

  override val arguments: Arguments? get() = element.getArguments()
}
