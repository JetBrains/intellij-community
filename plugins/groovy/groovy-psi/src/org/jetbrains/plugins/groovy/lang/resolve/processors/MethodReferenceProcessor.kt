// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor

internal open class MethodReferenceProcessor(methodName: String) : ProcessorWithCommonHints(), GrResolverProcessor<GroovyResolveResult> {

  init {
    nameHint(methodName)
    elementClassHint(ElementClassHint.DeclarationKind.METHOD)
  }

  private val myResults = SmartList<GroovyResolveResult>()

  final override val results: List<GroovyResolveResult> get() = myResults

  final override fun execute(element: PsiElement, state: ResolveState): Boolean {
    if (element is PsiMethod) {
      result(element, state)?.let {
        myResults += it
      }
    }
    return true
  }

  protected open fun result(method: PsiMethod, state: ResolveState): GroovyResolveResult? {
    val substitutor = state[PsiSubstitutor.KEY]
    return object : ElementResolveResult<PsiMethod>(method) {
      override fun getSubstitutor() = substitutor
    }
  }
}
