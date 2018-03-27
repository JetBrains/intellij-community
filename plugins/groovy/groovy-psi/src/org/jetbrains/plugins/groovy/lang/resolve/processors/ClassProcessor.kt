// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.resolve.AnnotationHint
import org.jetbrains.plugins.groovy.lang.resolve.ClassResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_CLASS

class ClassProcessor(
  name: String,
  private val place: PsiElement?,
  private val typeArguments: Array<out PsiType> = PsiType.EMPTY_ARRAY,
  private val annotationResolve: Boolean = false
) : GrScopeProcessorWithHints(name, RESOLVE_KINDS_CLASS), AnnotationHint, GrResolverProcessor<ClassResolveResult> {

  private val myResults = SmartList<ClassResolveResult>()
  override val results: List<ClassResolveResult> get() = myResults

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    val clazz = element as? PsiClass ?: return true
    if (clazz is PsiTypeParameter) return true

    val substitutor = state.get(PsiSubstitutor.KEY) ?: PsiSubstitutor.EMPTY
    myResults += ClassResolveResult(
      element = clazz,
      place = place,
      resolveContext = state.get(ClassHint.RESOLVE_CONTEXT),
      substitutor = substitutor.putAll(clazz, typeArguments)
    )
    return false
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> getHint(hintKey: Key<T>): T? {
    if (hintKey == AnnotationHint.HINT_KEY && annotationResolve) return this as T
    return super<GrScopeProcessorWithHints>.getHint(hintKey)
  }

  override fun isAnnotationResolve(): Boolean = annotationResolve
}
