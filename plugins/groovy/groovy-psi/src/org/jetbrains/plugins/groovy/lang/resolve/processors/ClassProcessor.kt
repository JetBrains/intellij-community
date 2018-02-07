// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import org.jetbrains.plugins.groovy.lang.resolve.AnnotationHint
import org.jetbrains.plugins.groovy.lang.resolve.ClassResolveResult

class ClassProcessor(
  name: String,
  private val place: PsiElement?,
  private val typeArguments: Array<out PsiType> = PsiType.EMPTY_ARRAY,
  annotationResolve: Boolean = false
) : FindFirstProcessor<ClassResolveResult>(name) {

  init {
    hint(ElementClassHint.KEY, ClassHint.CLASSES)
    if (annotationResolve) {
      hint(AnnotationHint.HINT_KEY, AnnotationHint.ANNOTATION_RESOLVE)
    }
  }

  override fun result(element: PsiElement, state: ResolveState): ClassResolveResult? {
    val clazz = element as? PsiClass ?: return null
    if (clazz is PsiTypeParameter) return null

    val substitutor = state.get(PsiSubstitutor.KEY) ?: PsiSubstitutor.EMPTY
    return ClassResolveResult(
      element = clazz,
      place = place,
      resolveContext = state.get(ClassHint.RESOLVE_CONTEXT),
      substitutor = substitutor.putAll(clazz, typeArguments)
    )
  }
}

