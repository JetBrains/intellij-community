// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker

class MethodRequestsBuilder(private val myCall: GrMethodCall) {

  private val myRequests = LinkedHashMap<JvmClass, CreateMethodRequest>()

  fun buildRequests(): Map<JvmClass, CreateMethodRequest> {
    build()
    return myRequests
  }

  private fun build() {
    val invokedExpression = myCall.invokedExpression as? GrReferenceExpression ?: return
    val targetClasses = getTargetClasses(invokedExpression) {
      psiClass -> psiClass.manager.isInProject(psiClass)
    }
    targetClasses.forEach {
      processClass(it, invokedExpression)
    }

  }

  private fun processClass(clazz: PsiClass, invokedExpression: GrReferenceExpression) {
    val modifiers = mutableSetOf<JvmModifier>()
    if (GrStaticChecker.isInStaticContext(invokedExpression, clazz)) modifiers += JvmModifier.STATIC
    myRequests[clazz] = CreateMethodFromGroovyUsageRequest(myCall, modifiers)
  }
}