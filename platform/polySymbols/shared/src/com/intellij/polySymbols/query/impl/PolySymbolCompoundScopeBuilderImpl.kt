// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.DependencyHandle
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.impl.DepSpec
import com.intellij.polySymbols.impl.DependencyHandleImpl
import com.intellij.polySymbols.impl.DependencyScope
import com.intellij.polySymbols.impl.DependencyScope.Companion.dependencyScope
import com.intellij.polySymbols.impl.DependencySource
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

    val source = DependencySource.fromSpecs(depSpecs.toList())
    val initialScope = source.dependencyScope()
    return BuiltPolySymbolCompoundScope(source, initialScope, requiresResolveValue, priorityValue, body)
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
  private val dependencySource: DependencySource,
  private val dependencyScope: DependencyScope,
  private val requiresResolveValue: Boolean,
  private val priorityValue: PolySymbol.Priority?,
  private val initBody: PolySymbolCompoundScopeInitializer.() -> Unit,
) : PolySymbolCompoundScope(), PolySymbolPrioritizedScope {

  override val priority: PolySymbol.Priority?
    get() = priorityValue

  override fun requiresResolve(): Boolean = requiresResolveValue

  override fun build(queryExecutor: PolySymbolQueryExecutor, consumer: (PolySymbolScope) -> Unit) {
    dependencyScope.withinScope {
      PolySymbolCompoundScopeInitializerImpl(queryExecutor, consumer).initBody()
    }
  }

  override fun createPointer(): Pointer<out PolySymbolCompoundScope> {
    if (dependencySource.isEmpty) return Pointer.hardPointer(this)
    val pointerSource = dependencySource.asFromPointers()
    val body = initBody
    val requiresResolveValue = requiresResolveValue
    val priorityValue = priorityValue
    return Pointer {
      BuiltPolySymbolCompoundScope(
        pointerSource, pointerSource.dependencyScope() ?: return@Pointer null,
        requiresResolveValue, priorityValue, body
      )
    }
  }

  override fun equals(other: Any?): Boolean =
    other === this
    || other is BuiltPolySymbolCompoundScope
    && other.priorityValue == priorityValue
    && other.requiresResolveValue == requiresResolveValue
    && other.dependencyScope.resolved == dependencyScope.resolved
    && other.initBody::class === initBody::class

  override fun hashCode(): Int {
    var result = dependencyScope.resolved.hashCode()
    result = 31 * result + initBody::class.hashCode()
    result = 31 * result + requiresResolveValue.hashCode()
    result = 31 * result + priorityValue.hashCode()
    return result
  }
}

internal fun buildPolySymbolCompoundScope(
  configure: PolySymbolCompoundScopeBuilder.() -> Unit,
): PolySymbolCompoundScope =
  PolySymbolCompoundScopeBuilderImpl(configure).build()
