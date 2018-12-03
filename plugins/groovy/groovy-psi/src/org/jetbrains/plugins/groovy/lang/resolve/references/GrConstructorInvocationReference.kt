// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.util.TypeConversionUtil.getSuperClassSubstitutor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.ClassResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

class GrConstructorInvocationReference(element: GrConstructorInvocation) : GrConstructorReference<GrConstructorInvocation>(element) {

  override fun resolveClass(): ClassResolveResult? {
    val invocation = element
    val clazz = invocation.delegatedClass ?: return null
    val state = if (invocation.isThisCall) {
      ResolveState.initial()
    }
    else {
      val enclosing = PsiUtil.getContextClass(invocation) ?: return null
      val substitutor = getSuperClassSubstitutor(clazz, enclosing, PsiSubstitutor.EMPTY)
      ResolveState.initial().put(PsiSubstitutor.KEY, substitutor)
    }
    return ClassResolveResult(clazz, invocation, state, PsiType.EMPTY_ARRAY)
  }

  override val arguments: Arguments? get() = element.getArguments()

  override val supportsMapInvocation: Boolean get() = false
}
