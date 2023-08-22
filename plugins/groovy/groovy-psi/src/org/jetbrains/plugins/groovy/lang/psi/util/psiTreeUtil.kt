// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.util.containers.withPrevious
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrSingleResultResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DECLARATION_SCOPE_PASSED

/**
 * @receiver element to start from
 * @return sequence of context elements
 */
fun PsiElement.contexts(): Sequence<PsiElement> = generateSequence(this) {
  ProgressManager.checkCanceled()
  it.context
}

fun PsiElement.backwardSiblings(): Sequence<PsiElement> = generateSequence(this) {
  ProgressManager.checkCanceled()
  it.prevSibling
}

@JvmOverloads
fun PsiElement.treeWalkUp(processor: PsiScopeProcessor, state: ResolveState = ResolveState.initial(), place: PsiElement = this): Boolean {
  for ((scope, lastParent) in contexts().withPrevious()) {
    if (!scope.processDeclarations(processor, state, lastParent, place)) return false
    processor.handleEvent(DECLARATION_SCOPE_PASSED, scope)
  }
  return true
}

fun <T : GroovyResolveResult> PsiElement.treeWalkUpAndGet(processor: GrSingleResultResolverProcessor<T>): T? {
  treeWalkUp(processor, ResolveState.initial(), this)
  return processor.result
}

fun <T : PsiElement> PsiElement.treeWalkUpAndGetElement(processor: GrSingleResultResolverProcessor<ElementResolveResult<T>>): T? {
  return treeWalkUpAndGet(processor)?.element
}

inline fun <reified T : PsiElement> PsiElement.skipParentsOfType(): Pair<PsiElement, PsiElement?>? = skipParentsOfType(true, T::class.java)

fun PsiElement.skipParentsOfType(strict: Boolean = false, vararg types: Class<*>): Pair<PsiElement, PsiElement?>? {
  val seq = parents(true).withPrevious().drop(if (strict) 1 else 0)
  return seq.firstOrNull { (parent, _) ->
    !PsiTreeUtil.instanceOf(parent, *types)
  }
}

inline fun <reified T : PsiElement> T.skipSameTypeParents(): Pair<PsiElement?, T> {
  var lastParent: T = this
  var parent: PsiElement? = parent
  while (parent is T) {
    lastParent = parent
    parent = parent.parent
  }
  return Pair(parent, lastParent)
}
