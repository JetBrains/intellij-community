/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DECLARATION_SCOPE_PASSED

fun PsiElement.getParents() = treeSequence(this) { it.parent }

/**
 * Creates sequence of pairs of elements corresponding to tree walk up by contexts.
 *
 * Each element of the sequence is a pair of [PsiElement]s
 * where the first element is current parent and the second element is previous parent.
 *
 * @receiver element to start from
 * @see treeWalkUp
 */
fun PsiElement.getContexts(): Sequence<Pair<PsiElement, PsiElement?>> = treeSequence(this) { it.context }

private fun treeSequence(start: PsiElement?, next: (PsiElement) -> PsiElement?) =
  object : Sequence<Pair<PsiElement, PsiElement?>> {
    override fun iterator() = treeIterator(start, next)
  }

private fun treeIterator(start: PsiElement?, next: (PsiElement) -> PsiElement?) =
  object : Iterator<Pair<PsiElement, PsiElement?>> {
    private var currentElement: PsiElement? = start
    private var previousElement: PsiElement? = null

    override fun hasNext() = currentElement != null

    override fun next(): Pair<PsiElement, PsiElement?> {
      ProgressManager.checkCanceled()
      val current = currentElement!!
      val result = current to previousElement
      previousElement = current
      currentElement = next(current)
      return result
    }
  }

@JvmOverloads
fun PsiElement.treeWalkUp(processor: PsiScopeProcessor, state: ResolveState = ResolveState.initial()): Boolean {
  for ((scope, lastParent) in getContexts()) {
    if (!scope.processDeclarations(processor, state, lastParent, this)) return false
    processor.handleEvent(DECLARATION_SCOPE_PASSED, scope)
  }
  return true
}

fun PsiElement.skipParentsOfType(vararg types: Class<*>): Pair<PsiElement, PsiElement?>? = skipParentsOfType(true, *types)

fun PsiElement.skipParentsOfType(strict: Boolean, vararg types: Class<*>): Pair<PsiElement, PsiElement?>? {
  val seq = if (strict) getParents().drop(1) else getParents()
  for (parents in seq) {
    if (parents.first.javaClass in types) continue
    return parents
  }
  return null
}
