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
import com.intellij.psi.util.parents
import com.intellij.util.withPrevious
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.DECLARATION_SCOPE_PASSED

/**
 * @receiver element to start from
 * @return sequence of context elements
 */
fun PsiElement.contexts(): Sequence<PsiElement> = generateSequence(this) {
  ProgressManager.checkCanceled()
  it.context
}

@JvmOverloads
fun PsiElement.treeWalkUp(processor: PsiScopeProcessor, state: ResolveState = ResolveState.initial()): Boolean {
  for ((scope, lastParent) in contexts().withPrevious()) {
    if (!scope.processDeclarations(processor, state, lastParent, this)) return false
    processor.handleEvent(DECLARATION_SCOPE_PASSED, scope)
  }
  return true
}

inline fun <reified T : PsiElement> PsiElement.skipParentsOfType() = skipParentsOfType(true, T::class.java)

fun PsiElement.skipParentsOfType(strict: Boolean = false, vararg types: Class<*>): Pair<PsiElement, PsiElement?>? {
  val seq = parents().withPrevious().drop(if (strict) 1 else 0)
  return seq.firstOrNull { (parent, _) ->
    parent.javaClass !in types
  }
}
