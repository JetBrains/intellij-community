/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.Consumer
import com.intellij.util.SmartList

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
    return collectDependencies(ref)?.mapNotNullTo(mutableListOf()) {
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

  protected open fun collectDependencies(ref: T): Collection<PsiPolyVariantReference>? {
    val result = SmartList<PsiPolyVariantReference>()
    collectDependencies(ref, Consumer {
      result += it
    })
    return result
  }

  protected open fun collectDependencies(ref: T, consumer: Consumer<in PsiPolyVariantReference>) {}

  protected abstract fun doResolve(ref: T, incomplete: Boolean): Array<out ResolveResult>
}