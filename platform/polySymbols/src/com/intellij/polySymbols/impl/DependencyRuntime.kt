// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.polySymbols.DependencyHandle
import kotlin.reflect.KProperty

internal class DependencyHandleImpl<T : Any>(internal val index: Int) : DependencyHandle<T> {

  override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    val scope = currentDependencyScope.get()
                ?: error("DependencyHandle `${property.name}` accessed outside a PolySymbol DSL getter lambda")
    @Suppress("UNCHECKED_CAST")
    return scope.resolved[index] as T
  }

  override val value: T
    get() = getValue(null, VALUE_READ_PROP)

  override operator fun invoke(): T =
    getValue(null, VALUE_READ_PROP)
}

private val currentDependencyScope: ThreadLocal<DependencyScope> = ThreadLocal()

internal val PSI_CONTEXT_READ_PROP: KProperty<*> = ::PSI_CONTEXT_READ_PROP
internal val VALUE_READ_PROP: KProperty<*> = ::VALUE_READ_PROP

/**
 * Read the current value of a [DependencyHandle] — assuming the caller has
 * ensured an ambient [DependencyScope] is active. Equivalent to reading a
 * `by dependency(...)` delegated property.
 */
internal fun <T : Any> DependencyHandle<T>.readInScope(): T =
  getValue(null, PSI_CONTEXT_READ_PROP)

/**
 * Receiver of every DSL getter lambda. Carries the pre-resolved values for
 * all declared dependencies of the currently-materialized symbol.
 */
internal class DependencyScope private constructor(internal val resolved: List<Any>) {

  /**
   * Runs [block] with this scope set as the ambient dependency scope,
   * restoring the previous value on exit. Reading any `by dependency(...)`
   * delegate inside [block] returns the pre-resolved value.
   */
  internal fun <R> withinScope(block: DependencyScope.() -> R): R {
    val prev = currentDependencyScope.get()
    currentDependencyScope.set(this)
    try {
      return this.block()
    }
    finally {
      currentDependencyScope.set(prev)
    }
  }

  /**
   * Runs [block] with this scope set as ambient, passing [arg] to the block.
   * Used for multi-arg getter lambdas (e.g. `documentationTarget(location) { … }`).
   */
  internal fun <A, R> withinScope(arg: A, block: DependencyScope.(A) -> R): R {
    val prev = currentDependencyScope.get()
    currentDependencyScope.set(this)
    try {
      return this.block(arg)
    }
    finally {
      currentDependencyScope.set(prev)
    }
  }

  companion object {

    fun DependencySource.FromPointers.dependencyScope(): DependencyScope? {
      return DependencyScope(snapshot() ?: return null)
    }

    fun DependencySource.FromSpecs.dependencyScope(): DependencyScope {
      return DependencyScope(snapshot())
    }
  }
}
