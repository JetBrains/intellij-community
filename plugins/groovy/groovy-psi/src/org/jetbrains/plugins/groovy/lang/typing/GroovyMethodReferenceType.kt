// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiMethod
import com.intellij.util.SmartList
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.MethodSignature

internal class GroovyMethodReferenceType(
  private val myMethodReference: GrReferenceExpression
) : GroovyClosureType(myMethodReference) {

  override val signatures: List<CallSignature<*>> by lazyPub {
    myMethodReference.resolve(false).mapNotNullTo(SmartList()) { result ->
      (result.element as? PsiMethod)?.let {
        MethodSignature(it, result.substitutor, myMethodReference)
      }
    }
  }
}
