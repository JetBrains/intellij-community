// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor

class GrConstructorInvocationReference(element: GrConstructorInvocation) : GroovyCachingReference<GrConstructorInvocation>(element) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val invocation = element
    val clazz = invocation.delegatedClass ?: return emptyList()
    val argTypes = PsiUtil.getArgumentTypes(invocation.firstChild, false)
    val substitutor: PsiSubstitutor = if (invocation.isThisCall) {
      PsiSubstitutor.EMPTY
    }
    else {
      val enclosing = PsiUtil.getContextClass(invocation) ?: return emptyList()
      TypeConversionUtil.getSuperClassSubstitutor(clazz, enclosing, PsiSubstitutor.EMPTY)
    }
    val thisType = JavaPsiFacade.getInstance(invocation.project).elementFactory.createType(clazz, substitutor)
    val processor = MethodResolverProcessor(clazz.name, invocation, true, thisType, argTypes, PsiType.EMPTY_ARRAY, incomplete)
    val state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor)
    clazz.processDeclarations(processor, state, null, invocation)
    ResolveUtil.processNonCodeMembers(thisType, processor, invocation.invokedExpression, state)
    return processor.candidates.toList()
  }
}
