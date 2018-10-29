// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getArgumentTypes
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReferenceBase

/**
 * Reference to an implicit `.call`.
 *
 * Appears in when method call expression has non-[reference][GrReferenceExpression] invoked expression,
 * such as `(a + b)()` is actually `(a + b).call()`,
 * or when invoked expression is a [reference][GrReferenceExpression]
 * which is an [implicit receiver][GrReferenceExpression.isImplicitCallReceiver].
 */
class GrImplicitCallReference(element: GrMethodCall) : GroovyMethodCallReferenceBase<GrMethodCall>(element) {

  override val isRealReference: Boolean get() = true

  override val receiver: PsiType? get() = element.invokedExpression.type

  override val methodName: String get() = "call"

  override val arguments: Arguments? get() = getArgumentTypes(element.argumentList)?.toList()
}
