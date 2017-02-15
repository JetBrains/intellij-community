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
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache

abstract class DependentResolver<T : PsiPolyVariantReference> : ResolveCache.PolyVariantResolver<T> {

  companion object {
    /**
     * Given: resolve was called on a reference a.r1.r2...rN.
     * Its dependencies: a, a.r1, a.r1.r2, ... , a.r1.r2...rN-1.
     * We resolve dependencies in a loop.
     * Assume currently resolving dependency is a.r1.r2...rK, K < N.
     * By the time it is being processed all its dependencies are already resolved and the resolve results are stored in a list,
     * so we do not need to collect/resolve its dependencies again.
     * This field is needed to memoize currently resolving dependencies.
     */
    private val resolvingDependencies = ThreadLocal.withInitial { mutableSetOf<PsiPolyVariantReference>() }
  }

  override final fun resolve(ref: T, incomplete: Boolean): Array<out ResolveResult> {
    val dependencies = resolveDependencies(ref, incomplete)
    val result = doResolve(ref, incomplete)
    dependencies?.clear()
    return result
  }

  private fun resolveDependencies(ref: T, incomplete: Boolean): MutableCollection<Any>? {
    if (ref in resolvingDependencies.get()) return null
    return collectDependencies(ref)?.mapNotNullTo(mutableListOf<Any>()) {
      if (ref === it) return@mapNotNullTo null
      try {
        resolvingDependencies.get().add(it)
        it.multiResolve(incomplete)
      }
      finally {
        resolvingDependencies.get().remove(it)
      }
    }
  }

  abstract fun collectDependencies(ref: T): Collection<PsiPolyVariantReference>?

  abstract fun doResolve(ref: T, incomplete: Boolean): Array<out ResolveResult>

}