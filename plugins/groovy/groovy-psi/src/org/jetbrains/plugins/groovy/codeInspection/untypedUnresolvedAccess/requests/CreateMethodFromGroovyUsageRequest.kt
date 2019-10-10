// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.actions.expectedType
import com.intellij.lang.jvm.actions.expectedTypes
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.util.containers.withPrevious
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider

internal class CreateMethodFromGroovyUsageRequest(
  methodCall: GrMethodCall,
  modifiers: Collection<JvmModifier>
) : CreateExecutableFromGroovyUsageRequest<GrMethodCall>(methodCall, modifiers), CreateMethodRequest {

  override fun isValid() = super.isValid() && call.let {
    getRefExpression()?.referenceName != null
  }

  private fun getRefExpression() = call.invokedExpression as? GrReferenceExpression

  override fun getMethodName() = getRefExpression()?.referenceName!!

  override fun getReturnType() : List<ExpectedType> {
    val expected = GroovyExpectedTypesProvider.getDefaultExpectedTypes(call)
    if (expected.isEmpty()) {
      return expectedTypes(PsiType.VOID)
    }
    return expected.map { expectedType(it, ExpectedType.Kind.EXACT) }
  }

  fun getAnchor(targetClass: PsiClass): PsiElement? {
    val enclosingMember = call.parentOfType(PsiMethod::class, PsiField::class, PsiClassInitializer::class) ?: return null
    for ((parent, lastParent) in enclosingMember.parents().withPrevious()) {
      if (parent == targetClass) return lastParent
    }
    return null
  }
}
