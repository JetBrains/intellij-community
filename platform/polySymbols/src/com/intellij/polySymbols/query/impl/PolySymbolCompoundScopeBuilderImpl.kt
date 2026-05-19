// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.DependencyHandle
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.impl.DepSpec
import com.intellij.polySymbols.impl.DependencyHandleImpl
import com.intellij.polySymbols.impl.DependencyScope
import com.intellij.polySymbols.impl.checkNoPsiCapture
import com.intellij.polySymbols.query.PolySymbolCompoundScope
import com.intellij.polySymbols.query.PolySymbolCompoundScopeBuilder
import com.intellij.polySymbols.query.PolySymbolCompoundScopeInitializer
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.utils.PolySymbolPrioritizedScope
import com.intellij.psi.PsiElement

internal class PolySymbolCompoundScopeBuilderImpl(
  configure: PolySymbolCompoundScopeBuilder.() -> Unit,
) : PolySymbolCompoundScopeBuilder {

  private var requiresResolveValue: Boolean = true
  private var priorityValue: PolySymbol.Priority? = null
  private val depSpecs: MutableList<DepSpec<*>> = mutableListOf()
  private var initBody: (PolySymbolCompoundScopeInitializer.() -> Unit)? = null

  init {
    configure()
  }

  override fun requiresResolve(value: Boolean) {
    requiresResolveValue = value
  }

  override fun priority(priority: PolySymbol.Priority) {
    priorityValue = priority
  }

  override fun <T : PsiElement> dependency(element: T): DependencyHandle<T> {
    val idx = depSpecs.size
    depSpecs += DepSpec.FromPsiElement(element)
    return DependencyHandleImpl(idx)
  }

  override fun <T : Any> dependency(`object`: T, pointerProvider: (T) -> Pointer<out T>): DependencyHandle<T> {
    val idx = depSpecs.size
    depSpecs += DepSpec.FromGenericObject(`object`, pointerProvider)
    return DependencyHandleImpl(idx)
  }

  override fun initialize(body: PolySymbolCompoundScopeInitializer.() -> Unit) {
    check(initBody == null) { "polySymbolCompoundScope: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolCompoundScope.initialize")
    initBody = body
  }

  fun build(): PolySymbolCompoundScope {
    val body = initBody ?: error("polySymbolCompoundScope: initialize { } was not called.")
    val originalValues = depSpecs.map { it.currentValue() }
    val pointers = depSpecs.map { it.toPointer() }
    return BuiltPolySymbolCompoundScope(originalValues, pointers, requiresResolveValue, priorityValue, body)
  }
}

private class PolySymbolCompoundScopeInitializerImpl(
  override val queryExecutor: PolySymbolQueryExecutor,
  private val consumer: (PolySymbolScope) -> Unit,
) : PolySymbolCompoundScopeInitializer {

  override fun add(scope: PolySymbolScope) = consumer(scope)

  override fun addAll(scopes: Iterable<PolySymbolScope>) = scopes.forEach(consumer)

  override fun PolySymbolScope.unaryPlus() = add(this)

  override fun Iterable<PolySymbolScope>.unaryPlus() = addAll(this)
}

private class BuiltPolySymbolCompoundScope(
  private val originalValues: List<Any>,
  private val pointers: List<Pointer<out Any>>,
  private val requiresResolveValue: Boolean,
  private val priorityValue: PolySymbol.Priority?,
  private val initBody: PolySymbolCompoundScopeInitializer.() -> Unit,
) : PolySymbolCompoundScope(), PolySymbolPrioritizedScope {

  override val priority: PolySymbol.Priority?
    get() = priorityValue

  override fun requiresResolve(): Boolean = requiresResolveValue

  override fun build(queryExecutor: PolySymbolQueryExecutor, consumer: (PolySymbolScope) -> Unit) {
    val resolved = pointers.map { it.dereference() ?: return }
    DependencyScope(resolved).withinScope {
      PolySymbolCompoundScopeInitializerImpl(queryExecutor, consumer).initBody()
    }
  }

  override fun createPointer(): Pointer<out PolySymbolCompoundScope> {
    if (pointers.isEmpty()) return Pointer.hardPointer(this)
    val deps = pointers
    val body = initBody
    val r = requiresResolveValue
    val p = priorityValue
    return Pointer {
      val resolved = deps.map { it.dereference() ?: return@Pointer null }
      BuiltPolySymbolCompoundScope(resolved, deps, r, p, body)
    }
  }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is BuiltPolySymbolCompoundScope &&
    other.originalValues == originalValues &&
    other.initBody === initBody

  override fun hashCode(): Int =
    31 * originalValues.hashCode() + System.identityHashCode(initBody)
}

internal fun buildPolySymbolCompoundScope(
  configure: PolySymbolCompoundScopeBuilder.() -> Unit,
): PolySymbolCompoundScope {
  checkNoPsiCapture(configure, "polySymbolCompoundScope.configure")
  return PolySymbolCompoundScopeBuilderImpl(configure).build()
}
