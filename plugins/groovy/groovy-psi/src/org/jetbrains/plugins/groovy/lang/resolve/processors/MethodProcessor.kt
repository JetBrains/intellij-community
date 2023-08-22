// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.resolve.BaseMethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.MethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll

class MethodProcessor(
  name: String,
  private val place: PsiElement,
  private val arguments: Arguments?,
  private val typeArguments: Array<out PsiType>
) : BaseMethodProcessor(name),
    GroovyResolveKind.Hint,
    ElementClassHint,
    DynamicMembersHint {

  init {
    hint(GroovyResolveKind.HINT_KEY, this)
    hint(ElementClassHint.KEY, this)
    hint(DynamicMembersHint.KEY, this)
  }

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind == GroovyResolveKind.METHOD && acceptMore

  override fun shouldProcess(kind: DeclarationKind): Boolean = kind == DeclarationKind.METHOD && acceptMore

  override fun shouldProcessMethods(): Boolean = myCandidates.isEmpty()

  override fun candidate(element: PsiMethod, state: ResolveState): GroovyMethodResult {
    return when {
      !element.hasTypeParameters() -> {
        // ignore explicit type arguments if there are no type parameters => no inference needed
        BaseMethodResolveResult(element, place, state, arguments)
      }
      typeArguments.isEmpty() -> {
        // generic method call without explicit type arguments => needs inference
        MethodResolveResult(element, place, state, arguments)
      }
      else -> {
        // generic method call with explicit type arguments => inference happens right here
        val substitutor = state[PsiSubstitutor.KEY].putAll(element.typeParameters, typeArguments)
        BaseMethodResolveResult(element, place, state.put(PsiSubstitutor.KEY, substitutor), arguments)
      }
    }
  }
}
