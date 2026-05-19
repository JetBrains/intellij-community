// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolBuilder
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.impl.checkNoPsiCapture
import com.intellij.polySymbols.polySymbol
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScopeCachedBuilder
import com.intellij.polySymbols.query.PolySymbolScopeCachedBuilderBase
import com.intellij.polySymbols.query.PolySymbolScopeCachedInitializer
import com.intellij.polySymbols.query.PolySymbolScopeCachedInitializerBase
import com.intellij.polySymbols.utils.PolySymbolScopeWithCache
import com.intellij.polySymbols.utils.ReferencingPolySymbol
import com.intellij.polySymbols.query.ProjectPolySymbolScopeCachedBuilder
import com.intellij.polySymbols.query.ProjectPolySymbolScopeCachedInitializer
import com.intellij.polySymbols.query.PsiPolySymbolScopeCachedBuilder
import com.intellij.polySymbols.query.PsiPolySymbolScopeCachedInitializer
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

internal abstract class AbstractBuilder<K>(
  override val project: Project,
  override val key: K,
) : PolySymbolScopeCachedBuilderBase<K> {

  val providesKinds: MutableSet<PolySymbolKind> = mutableSetOf()

  val exclusiveForKinds: MutableSet<PolySymbolKind> = mutableSetOf()

  var exclusiveForPredicate: ((PolySymbolKind) -> Boolean)? = null
    private set

  var requiresResolveValue: Boolean = true
    private set

  var codeCompletionFilter: ((kind: PolySymbolKind, items: List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>)? = null
    private set

  var nameMatchFilter: ((qualifiedName: PolySymbolQualifiedName, matches: List<PolySymbol>) -> List<PolySymbol>)? = null
    private set

  final override fun provides(vararg kinds: PolySymbolKind) {
    providesKinds.addAll(kinds)
  }

  final override fun provides(kinds: Collection<PolySymbolKind>) {
    providesKinds.addAll(kinds)
  }

  final override fun exclusiveFor(vararg kinds: PolySymbolKind) {
    exclusiveForKinds.addAll(kinds)
  }

  final override fun exclusiveFor(kinds: Collection<PolySymbolKind>) {
    exclusiveForKinds.addAll(kinds)
  }

  final override fun exclusiveFor(predicate: (PolySymbolKind) -> Boolean) {
    checkNoPsiCapture(predicate, "polySymbolScopeCached.exclusiveFor")
    exclusiveForPredicate = predicate
  }

  final override fun requiresResolve(value: Boolean) {
    requiresResolveValue = value
  }

  final override fun filterCodeCompletions(
    filter: (kind: PolySymbolKind, items: List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>,
  ) {
    checkNoPsiCapture(filter, "polySymbolScopeCached.filterCodeCompletions")
    codeCompletionFilter = filter
  }

  final override fun filterNameMatches(
    filter: (qualifiedName: PolySymbolQualifiedName, matches: List<PolySymbol>) -> List<PolySymbol>,
  ) {
    checkNoPsiCapture(filter, "polySymbolScopeCached.filterNameMatches")
    nameMatchFilter = filter
  }
}

internal class ProjectPolySymbolScopeCachedBuilderImpl<K>(
  project: Project,
  key: K,
  private val configure: ProjectPolySymbolScopeCachedBuilder<K>.() -> Unit,
) : AbstractBuilder<K>(project, key), ProjectPolySymbolScopeCachedBuilder<K> {

  init {
    checkNoPsiCapture(configure, "polySymbolScopeCached.configure")
  }

  private var initBody: (ProjectPolySymbolScopeCachedInitializer<K>.() -> Unit)? = null

  override fun initialize(body: ProjectPolySymbolScopeCachedInitializer<K>.() -> Unit) {
    check(initBody == null) { "polySymbolScopeCached: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolScopeCached.initialize")
    initBody = body
  }

  fun build(): BuiltPolySymbolScopeWithCache<Project, K> {
    configure(this)
    val body = initBody ?: error("polySymbolScopeCached: initialize { } was not called.")
    check(providesKinds.isNotEmpty()) { "polySymbolScopeCached: provides() must be called with at least one kind." }
    val projectRef = project
    val keyRef = key
    val configureRef = configure
    return BuiltPolySymbolScopeWithCache(
      project = projectRef,
      dataHolder = projectRef,
      scopeClass = configureRef::class.java,
      userKey = keyRef,
      providesKinds = providesKinds.toHashSet(),
      exclusiveForKinds = exclusiveForKinds.toHashSet(),
      exclusiveForPredicate = exclusiveForPredicate,
      requiresResolveValue = requiresResolveValue,
      codeCompletionFilter = codeCompletionFilter,
      nameMatchFilter = nameMatchFilter,
      pointerProvider = { Pointer.hardPointer(projectRef) },
      initializerFactory = { snapshotProject, _, snapshotKey, consumer, deps ->
        ProjectCachedInitializerImpl(snapshotProject, snapshotKey, consumer, deps)
      },
      initBody = {
        @Suppress("UNCHECKED_CAST")
        body.invoke(this as ProjectPolySymbolScopeCachedInitializer<K>)
      },
      reconstruct = { newProject ->
        ProjectPolySymbolScopeCachedBuilderImpl(newProject, keyRef, configureRef).build()
      },
    )
  }
}

internal class PsiPolySymbolScopeCachedBuilderImpl<T : PsiElement, K>(
  override val element: T,
  key: K,
  private val configure: PsiPolySymbolScopeCachedBuilder<T, K>.() -> Unit,
) : AbstractBuilder<K>(element.project, key), PsiPolySymbolScopeCachedBuilder<T, K> {

  init {
    checkNoPsiCapture(configure, "polySymbolScopeCached.configure")
  }

  private var initBody: (PsiPolySymbolScopeCachedInitializer<T, K>.() -> Unit)? = null

  override fun initialize(body: PsiPolySymbolScopeCachedInitializer<T, K>.() -> Unit) {
    check(initBody == null) { "polySymbolScopeCached: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolScopeCached.initialize")
    initBody = body
  }

  fun build(): BuiltPolySymbolScopeWithCache<T, K> {
    configure(this)
    val body = initBody ?: error("polySymbolScopeCached: initialize { } was not called.")
    check(providesKinds.isNotEmpty()) { "polySymbolScopeCached: provides() must be called with at least one kind." }
    val keyRef = key
    val configureRef = configure
    return BuiltPolySymbolScopeWithCache(
      project = project,
      dataHolder = element,
      scopeClass = configureRef::class.java,
      userKey = keyRef,
      providesKinds = providesKinds.toHashSet(),
      exclusiveForKinds = exclusiveForKinds.toHashSet(),
      exclusiveForPredicate = exclusiveForPredicate,
      requiresResolveValue = requiresResolveValue,
      codeCompletionFilter = codeCompletionFilter,
      nameMatchFilter = nameMatchFilter,
      pointerProvider = { it.createSmartPointer() },
      initializerFactory = { snapshotProject, snapshotHolder, snapshotKey, consumer, deps ->
        PsiCachedInitializerImpl(snapshotProject, snapshotHolder, snapshotKey, consumer, deps)
      },
      initBody = {
        @Suppress("UNCHECKED_CAST")
        body.invoke(this as PsiPolySymbolScopeCachedInitializer<T, K>)
      },
      reconstruct = { newElement ->
        PsiPolySymbolScopeCachedBuilderImpl(newElement, keyRef, configureRef).build()
      },
    )
  }
}

internal class UserDataHolderPolySymbolScopeCachedBuilderImpl<T : UserDataHolder, K>(
  project: Project,
  override val dataHolder: T,
  key: K,
  private val configure: PolySymbolScopeCachedBuilder<T, K>.() -> Unit,
) : AbstractBuilder<K>(project, key), PolySymbolScopeCachedBuilder<T, K> {

  init {
    checkNoPsiCapture(configure, "polySymbolScopeCached.configure")
  }

  private var initBody: (PolySymbolScopeCachedInitializer<T, K>.() -> Unit)? = null
  private var pointerProvider: ((T) -> Pointer<out T>)? = null

  override fun pointer(provider: (T) -> Pointer<out T>) {
    check(pointerProvider == null) { "polySymbolScopeCached: pointer { } must be called exactly once." }
    checkNoPsiCapture(provider, "polySymbolScopeCached.pointer")
    pointerProvider = provider
  }

  override fun initialize(body: PolySymbolScopeCachedInitializer<T, K>.() -> Unit) {
    check(initBody == null) { "polySymbolScopeCached: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolScopeCached.initialize")
    initBody = body
  }

  fun build(): BuiltPolySymbolScopeWithCache<T, K> {
    configure(this)
    val body = initBody ?: error("polySymbolScopeCached: initialize { } was not called.")
    check(providesKinds.isNotEmpty()) { "polySymbolScopeCached: provides() must be called with at least one kind." }
    val pointer = pointerProvider
                  ?: error("polySymbolScopeCached: pointer { } is required for non-PsiElement/non-Project holders.")
    val projectRef = project
    val keyRef = key
    val configureRef = configure
    return BuiltPolySymbolScopeWithCache(
      project = projectRef,
      dataHolder = dataHolder,
      scopeClass = configureRef::class.java,
      userKey = keyRef,
      providesKinds = providesKinds.toHashSet(),
      exclusiveForKinds = exclusiveForKinds.toHashSet(),
      exclusiveForPredicate = exclusiveForPredicate,
      requiresResolveValue = requiresResolveValue,
      codeCompletionFilter = codeCompletionFilter,
      nameMatchFilter = nameMatchFilter,
      pointerProvider = pointer,
      initializerFactory = { snapshotProject, snapshotHolder, snapshotKey, consumer, deps ->
        UserDataHolderCachedInitializerImpl(snapshotProject, snapshotHolder, snapshotKey, consumer, deps)
      },
      initBody = {
        @Suppress("UNCHECKED_CAST")
        body.invoke(this as PolySymbolScopeCachedInitializer<T, K>)
      },
      reconstruct = { newHolder ->
        UserDataHolderPolySymbolScopeCachedBuilderImpl(projectRef, newHolder, keyRef, configureRef).build()
      },
    )
  }
}

// ─── Initializer impls ────────────────────────────────────────────────────────

private abstract class AbstractCachedInitializer<K>(
  override val project: Project,
  override val key: K,
  private val consumer: (PolySymbol) -> Unit,
  private val cacheDeps: MutableSet<Any>,
) : PolySymbolScopeCachedInitializerBase<K> {

  final override fun cacheDependencies(vararg dependencies: Any) {
    for (dep in dependencies) cacheDeps.add(dep)
  }

  final override fun add(symbol: PolySymbol) {
    consumer(symbol)
  }

  final override fun addAll(symbols: Iterable<PolySymbol>) {
    symbols.forEach(consumer)
  }

  final override fun PolySymbol.unaryPlus() {
    consumer(this)
  }

  final override fun Iterable<PolySymbol>.unaryPlus() {
    forEach(consumer)
  }

  final override fun addSymbol(
    kind: PolySymbolKind,
    name: String,
    body: PolySymbolBuilder.() -> Unit,
  ) {
    consumer(polySymbol(kind, name, body))
  }

  final override fun referenceSymbols(
    kind: PolySymbolKind,
    displayName: String,
    vararg referencedKinds: PolySymbolKind,
    priority: PolySymbol.Priority?,
  ) {
    consumer(ReferencingPolySymbol.create(kind, displayName, *referencedKinds, priority = priority))
  }
}

private class ProjectCachedInitializerImpl<K>(
  project: Project,
  key: K,
  consumer: (PolySymbol) -> Unit,
  cacheDeps: MutableSet<Any>,
) : AbstractCachedInitializer<K>(project, key, consumer, cacheDeps),
    ProjectPolySymbolScopeCachedInitializer<K>

private class PsiCachedInitializerImpl<T : PsiElement, K>(
  project: Project,
  override val element: T,
  key: K,
  consumer: (PolySymbol) -> Unit,
  cacheDeps: MutableSet<Any>,
) : AbstractCachedInitializer<K>(project, key, consumer, cacheDeps),
    PsiPolySymbolScopeCachedInitializer<T, K>

private class UserDataHolderCachedInitializerImpl<T : UserDataHolder, K>(
  project: Project,
  override val dataHolder: T,
  key: K,
  consumer: (PolySymbol) -> Unit,
  cacheDeps: MutableSet<Any>,
) : AbstractCachedInitializer<K>(project, key, consumer, cacheDeps),
    PolySymbolScopeCachedInitializer<T, K>

// ─── Built scope ──────────────────────────────────────────────────────────────

internal class BuiltPolySymbolScopeWithCache<T : UserDataHolder, K>(
  project: Project,
  dataHolder: T,
  scopeClass: Class<*>,
  private val userKey: K,
  private val providesKinds: Set<PolySymbolKind>,
  private val exclusiveForKinds: Set<PolySymbolKind>,
  private val exclusiveForPredicate: ((PolySymbolKind) -> Boolean)?,
  private val requiresResolveValue: Boolean,
  private val codeCompletionFilter: ((PolySymbolKind, List<PolySymbolCodeCompletionItem>) -> List<PolySymbolCodeCompletionItem>)?,
  private val nameMatchFilter: ((PolySymbolQualifiedName, List<PolySymbol>) -> List<PolySymbol>)?,
  private val pointerProvider: (T) -> Pointer<out T>,
  private val initializerFactory: (
    Project,
    T,
    K,
    (PolySymbol) -> Unit,
    MutableSet<Any>,
  ) -> PolySymbolScopeCachedInitializerBase<K>,
  private val initBody: PolySymbolScopeCachedInitializerBase<K>.() -> Unit,
  private val reconstruct: (T) -> BuiltPolySymbolScopeWithCache<T, K>,
) : PolySymbolScopeWithCache<T, Pair<Class<*>, K>>(project, dataHolder, scopeClass to userKey) {

  override fun provides(kind: PolySymbolKind): Boolean = kind in providesKinds

  override fun isExclusiveFor(kind: PolySymbolKind): Boolean =
    kind in exclusiveForKinds || exclusiveForPredicate?.invoke(kind) == true

  override val requiresResolve: Boolean
    get() = requiresResolveValue

  override fun initialize(consumer: (PolySymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
    val initializer = initializerFactory(project, dataHolder, userKey, consumer, cacheDependencies)
    initBody.invoke(initializer)
  }

  override fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> {
    val base = super.getMatchingSymbols(qualifiedName, params, stack)
    val filter = nameMatchFilter ?: return base
    return filter(qualifiedName, base)
  }

  override fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> {
    val base = super.getCodeCompletions(qualifiedName, params, stack)
    val filter = codeCompletionFilter ?: return base
    return filter(qualifiedName.kind, base)
  }

  override fun createPointer(): Pointer<out BuiltPolySymbolScopeWithCache<T, K>> {
    val dataPointer = pointerProvider(dataHolder)
    val reconstruct = this.reconstruct
    return Pointer {
      dataPointer.dereference()?.let { reconstruct(it) }
    }
  }
}
