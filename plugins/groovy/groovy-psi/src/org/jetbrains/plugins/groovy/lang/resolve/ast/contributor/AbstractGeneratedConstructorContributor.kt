// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast.contributor

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.resolve.ClosureMemberContributor
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes
import org.jetbrains.plugins.groovy.lang.resolve.ast.constructorGeneratingAnnotations

abstract class AbstractGeneratedConstructorContributor : ClosureMemberContributor() {
  override fun processMembers(closure: GrClosableBlock, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    if (failFastCheck(processor, state)) return
    if (closure != place.parentOfType<GrClosableBlock>()) return
    val anno = closure.parentOfType<PsiAnnotation>()?.takeIf { it.qualifiedName in constructorGeneratingAnnotations } ?: return
    val elements = when (closure) {
      GrAnnotationUtil.inferClosureAttribute(anno, TupleConstructorAttributes.PRE) -> generateSyntheticElements(anno, closure, TupleConstructorAttributes.PRE)
      GrAnnotationUtil.inferClosureAttribute(anno, TupleConstructorAttributes.POST) -> generateSyntheticElements(anno, closure, TupleConstructorAttributes.POST)
      else -> return
    }
    for (element in elements) {
      if (!processor.execute(element, state)) {
        return
      }
    }
  }

  abstract fun failFastCheck(processor: PsiScopeProcessor, state: ResolveState): Boolean
  abstract fun generateSyntheticElements(annotation: PsiAnnotation, closure: GrClosableBlock, mode: String): Iterable<PsiElement>
}