// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getArgumentTypes
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReferenceBase

class GrMethodCallReference(element: GrMethodCall) : GroovyMethodCallReferenceBase<GrMethodCall>(element) {

  override val isRealReference: Boolean
    get() {
      val call = element
      val invoked = call.invokedExpression
      if (invoked is GrReferenceExpression) {
        return when {
          invoked.hasMemberPointer() -> {
            // `a.&foo()` compiles into `new MethodClosure(a, "foo").call()` as if `call` was explicitly in the code 
            true
          }
          invoked.hasAt() -> {
            // `a.@foo()` compiles into `a@.foo.call()` as if `call` was an explicitly in the code
            true
          }
          else -> invoked.staticReference.resolve() is GrVariable
        }
      }
      else {
        return true
      }
    }
  override val receiver: PsiType? get() = element.invokedExpression.type

  override val methodName: String get() = "call"

  override val arguments: Arguments? get() = getArgumentTypes(element.argumentList)?.toList()

  override fun fakeResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    return (element.invokedExpression as GrReferenceExpression).resolve(incomplete)
  }
}
