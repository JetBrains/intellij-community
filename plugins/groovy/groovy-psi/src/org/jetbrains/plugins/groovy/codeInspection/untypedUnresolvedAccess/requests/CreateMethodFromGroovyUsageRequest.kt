// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.actions.expectedType
import com.intellij.lang.jvm.actions.expectedTypes
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

internal class CreateMethodFromGroovyUsageRequest(
  methodCall: GrMethodCall,
  modifiers: Collection<JvmModifier>
) : CreateExecutableFromGroovyUsageRequest<GrMethodCall>(methodCall, modifiers), CreateMethodRequest {

  override fun getArguments(): List<Argument>? {
    return call.getArguments()
  }

  override fun isValid() = super.isValid() && call.let {
    getRefExpression()?.referenceName != null
  }

  private fun getRefExpression() : GrReferenceExpression? = if (super.isValid()) call.invokedExpression as? GrReferenceExpression else null

  override fun getMethodName() = getRefExpression()?.referenceName ?: "sample"

  override fun getReturnType() : List<ExpectedType> {
    val expected = GroovyExpectedTypesProvider.getDefaultExpectedTypes(call)
    if (expected.isEmpty()) {
      return expectedTypes(PsiType.VOID)
    }
    return expected.map { expectedType(it, ExpectedType.Kind.EXACT) }
  }
}
