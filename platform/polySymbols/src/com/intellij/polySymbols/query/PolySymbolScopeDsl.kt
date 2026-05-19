// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolBuilder
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.DependencyHandle
import com.intellij.polySymbols.query.impl.ProjectPolySymbolScopeCachedBuilderImpl
import com.intellij.polySymbols.query.impl.PsiPolySymbolScopeCachedBuilderImpl
import com.intellij.polySymbols.query.impl.UserDataHolderPolySymbolScopeCachedBuilderImpl
import com.intellij.polySymbols.query.impl.buildPolySymbolCompoundScope
import com.intellij.polySymbols.query.impl.buildPolySymbolScope
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * DSL marker for the `polySymbolScopeCached` builder hierarchy. Keeps the scope
 * and initializer receivers separate from the [com.intellij.polySymbols.PolySymbolDsl]
 * marker so that `polySymbol { }` can still be called inside an `initialize { }` body.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class PolySymbolScopeDsl

/**
 * Build a cached [PolySymbolScope] for an arbitrary [PsiElement] holder with a
 * non-Unit discriminator [key]. The project is derived from the element; the
 * internal pointer is created via `element.createSmartPointer()`.
 */
fun <T : PsiElement, K> polySymbolScopeCached(
  element: T,
  key: K,
  configure: PsiPolySymbolScopeCachedBuilder<T, K>.() -> Unit,
): PolySymbolScope =
  PsiPolySymbolScopeCachedBuilderImpl(element, key, configure).build()

/**
 * Build a cached [PolySymbolScope] for a [PsiElement] holder where only one
 * scope exists per element (key = `Unit`).
 */
fun <T : PsiElement> polySymbolScopeCached(
  element: T,
  configure: PsiPolySymbolScopeCachedBuilder<T, Unit>.() -> Unit,
): PolySymbolScope =
  PsiPolySymbolScopeCachedBuilderImpl(element, Unit, configure).build()

/**
 * Build a project-level cached [PolySymbolScope] where only one scope exists
 * per project (key = `Unit`). The pointer is a hard pointer to [project].
 */
fun polySymbolScopeCached(
  project: Project,
  configure: ProjectPolySymbolScopeCachedBuilder<Unit>.() -> Unit,
): PolySymbolScope =
  ProjectPolySymbolScopeCachedBuilderImpl(project, Unit, configure).build()

/**
 * Build a cached [PolySymbolScope] for an arbitrary [UserDataHolder]. The
 * caller must supply the [PolySymbolScopeCachedBuilder.pointer] strategy; the
 * impl throws [IllegalStateException] at build time if it is missing.
 */
fun <T : UserDataHolder, K> polySymbolScopeCached(
  project: Project,
  dataHolder: T,
  key: K,
  configure: PolySymbolScopeCachedBuilder<T, K>.() -> Unit,
): PolySymbolScope =
  UserDataHolderPolySymbolScopeCachedBuilderImpl(project, dataHolder, key, configure).build()

/**
 * Build a cached [PolySymbolScope] for an arbitrary [UserDataHolder] where
 * only one scope exists per holder (key = `Unit`).
 */
fun <T : UserDataHolder> polySymbolScopeCached(
  project: Project,
  dataHolder: T,
  configure: PolySymbolScopeCachedBuilder<T, Unit>.() -> Unit,
): PolySymbolScope =
  UserDataHolderPolySymbolScopeCachedBuilderImpl(project, dataHolder, Unit, configure).build()

/**
 * Build a simple, non-cached [PolySymbolScope] for a small, fixed symbol set.
 * In case of large [PolySymbol] scopes, which should be invalidated using
 * specific ModificationTrackers, prefer [polySymbolScopeCached].
 */
fun polySymbolScope(
  configure: PolySymbolScopeBuilder.() -> Unit,
): PolySymbolScope =
  buildPolySymbolScope(configure)

/**
 * Build a [PolySymbolCompoundScope] with the DSL. Declare dependencies via
 * [PolySymbolCompoundScopeBuilder.dependency] and emit scopes inside
 * [PolySymbolCompoundScopeBuilder.initialize].
 */
fun polySymbolCompoundScope(
  configure: PolySymbolCompoundScopeBuilder.() -> Unit,
): PolySymbolCompoundScope =
  buildPolySymbolCompoundScope(configure)


@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeBuilderBase {

  /**
   * Restrict the scope to a fixed set of [PolySymbolKind]s. Multiple calls are
   * additive.
   */
  fun provides(vararg kinds: PolySymbolKind)

  /**
   * Additive collection-form overload of [provides].
   */
  fun provides(kinds: Collection<PolySymbolKind>)

  /**
   * Mark this scope as exclusive for a fixed set of [PolySymbolKind]s. Multiple
   * calls are additive. Mirrors [PolySymbolScope.isExclusiveFor].
   */
  fun exclusiveFor(vararg kinds: PolySymbolKind)

  /**
   * Additive collection-form overload of [exclusiveFor].
   */
  fun exclusiveFor(kinds: Collection<PolySymbolKind>)

  /**
   * Mark this scope as exclusive via a predicate. Combined with the
   * [exclusiveFor] set via logical OR; overwrites any previous
   * predicate-form call.
   */
  fun exclusiveFor(predicate: (PolySymbolKind) -> Boolean)

  fun requiresResolve(value: Boolean)

  fun filterCodeCompletions(filter: (kind: PolySymbolKind, items: List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>)

  fun filterNameMatches(filter: (name: PolySymbolQualifiedName, matches: List<PolySymbol>) -> List<PolySymbol>)

}

/**
 * Builder receiver for the non-cached [polySymbolScope] factory. Declare the
 * scope's provided kinds, filters, and — via [initialize] — its symbols. The
 * [initialize] body runs lazily on first query or [PolySymbolScope.createPointer].
 */
@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeBuilder: PolySymbolScopeBuilderBase {

  /**
   * Declare the scope's symbols lazily. The [body] runs the first time the
   * scope is queried (`getSymbols`, `getMatchingSymbols`, `getCodeCompletions`)
   * or [PolySymbolScope.createPointer] is invoked.
   */
  fun initialize(body: PolySymbolScopeInitializer.() -> Unit)
}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeCachedBuilderBase<K>: PolySymbolScopeBuilderBase {

  val project: Project

  val key: K

}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PsiPolySymbolScopeCachedBuilder<T : PsiElement, K> : PolySymbolScopeCachedBuilderBase<K> {

  val element: T

  fun initialize(body: PsiPolySymbolScopeCachedInitializer<T, K>.() -> Unit)
}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface ProjectPolySymbolScopeCachedBuilder<K> : PolySymbolScopeCachedBuilderBase<K> {

  fun initialize(body: ProjectPolySymbolScopeCachedInitializer<K>.() -> Unit)
}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeCachedBuilder<T : UserDataHolder, K> : PolySymbolScopeCachedBuilderBase<K> {

  val dataHolder: T

  /**
   * Required. Describes how to recreate the [dataHolder] inside the pointer
   * returned by [PolySymbolScope.createPointer]. Must be called exactly once.
   */
  fun pointer(provider: (T) -> Pointer<out T>)

  fun initialize(body: PolySymbolScopeCachedInitializer<T, K>.() -> Unit)
}

/**
 * Receiver of the [PolySymbolScopeBuilder.initialize] body. Collects the scope's
 * symbols. No `project`/`key`/`dataHolder` context — the non-cached scope carries
 * none.
 */
@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeInitializer {

  /** Emit a single symbol to the scope. */
  fun add(symbol: PolySymbol)

  /** Emit a collection of symbols to the scope. */
  fun addAll(symbols: Iterable<PolySymbol>)

  /** Operator form of [add]. */
  operator fun PolySymbol.unaryPlus()

  /** Operator form of [addAll]. */
  operator fun Iterable<PolySymbol>.unaryPlus()

  /**
   * Convenience: build a [PolySymbol] with the existing
   * [com.intellij.polySymbols.polySymbol] DSL and add it to this scope in
   * one call. Equivalent to `add(polySymbol(kind, name) { ... })`.
   */
  fun addSymbol(
    kind: PolySymbolKind,
    name: String,
    body: PolySymbolBuilder.() -> Unit = {},
  )

  /**
   * Convenience method: adds a ReferencingPolySymbol to the scope.
   */
  fun referenceSymbols(
    kind: PolySymbolKind,
    displayName: String,
    vararg referencedKinds: PolySymbolKind,
    priority: PolySymbol.Priority? = null,
  )
}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeCachedInitializerBase<K>: PolySymbolScopeInitializer {

  val project: Project

  val key: K

  /**
   * Register one or more cache dependency trackers. The accumulated set must
   * end up non-empty — use [com.intellij.openapi.util.ModificationTracker.NEVER_CHANGED]
   * for scopes that never change.
   */
  fun cacheDependencies(vararg dependencies: Any)

}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface ProjectPolySymbolScopeCachedInitializer<K> : PolySymbolScopeCachedInitializerBase<K>

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PsiPolySymbolScopeCachedInitializer<T : PsiElement, K> : PolySymbolScopeCachedInitializerBase<K> {

  val element: T
}

@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolScopeCachedInitializer<T : UserDataHolder, K> : PolySymbolScopeCachedInitializerBase<K> {

  val dataHolder: T
}

/**
 * Receiver of the [PolySymbolCompoundScopeBuilder.initialize] body. Provides access to the
 * [queryExecutor] and methods to emit inner [PolySymbolScope]s to the compound scope.
 */
@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolCompoundScopeInitializer {

  val queryExecutor: PolySymbolQueryExecutor

  /** Emit a single scope. */
  fun add(scope: PolySymbolScope)

  /** Emit a collection of scopes. */
  fun addAll(scopes: Iterable<PolySymbolScope>)

  /** Operator form of [add]. */
  operator fun PolySymbolScope.unaryPlus()

  /** Operator form of [addAll]. */
  operator fun Iterable<PolySymbolScope>.unaryPlus()
}

/**
 * Builder receiver for the [polySymbolCompoundScope] factory. Declare optional priority and
 * `requiresResolve` flag, register dependencies via [dependency], and emit scopes in [initialize].
 */
@PolySymbolScopeDsl
@ApiStatus.NonExtendable
interface PolySymbolCompoundScopeBuilder {

  fun requiresResolve(value: Boolean)

  fun priority(priority: PolySymbol.Priority)

  /**
   * Declare a [PsiElement] dependency tracked via a smart pointer.
   * The returned [DependencyHandle] can be accessed (via `by`, `.value`, or `invoke()`)
   * inside the [initialize] body, where the dependency scope is active.
   */
  fun <T : PsiElement> dependency(element: T): DependencyHandle<T>

  /**
   * Declare a generic dependency with a custom pointer provider.
   */
  fun <T : Any> dependency(`object`: T, pointerProvider: (T) -> Pointer<out T>): DependencyHandle<T>

  fun initialize(body: PolySymbolCompoundScopeInitializer.() -> Unit)
}
