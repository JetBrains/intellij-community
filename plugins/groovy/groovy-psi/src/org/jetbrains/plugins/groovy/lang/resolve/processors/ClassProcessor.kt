// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.AnnotationHint
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.DiamondResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.imports.importedNameKey

internal open class ClassProcessor(
  private val name: String,
  private val place: PsiElement,
  private val typeArguments: Array<out PsiType>? = null,
  annotationResolve: Boolean = false
) : FindFirstProcessor<GroovyResolveResult>() {

  init {
    nameHint(name)
    elementClassHint(ElementClassHint.DeclarationKind.CLASS)
    if (annotationResolve) {
      hint(AnnotationHint.HINT_KEY, AnnotationHint.ANNOTATION_RESOLVE)
    }
  }

  override fun result(element: PsiElement, state: ResolveState): GroovyResolveResult? {
    val clazz = element as? PsiClass ?: return null
    if (clazz is PsiTypeParameter) return null

    val elementName = state[importedNameKey] ?: clazz.name
    if (elementName != name) return null

    return createResult(clazz, place, state, typeArguments)
  }

  companion object {
    fun createResult(clazz: PsiClass, place: PsiElement, state: ResolveState, typeArguments: Array<out PsiType>?): GroovyResolveResult {
      if (typeArguments == null || !typeArguments.isEmpty()) {
        val newSubstitutor = state[PsiSubstitutor.KEY].putAll(clazz, typeArguments)
        val newState = state.put(PsiSubstitutor.KEY, newSubstitutor)
        return BaseGroovyResolveResult(clazz, place, newState)
      }
      else {
        return DiamondResolveResult(clazz, place, state)
      }
    }
  }
}
