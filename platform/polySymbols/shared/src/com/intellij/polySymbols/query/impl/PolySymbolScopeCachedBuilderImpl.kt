// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
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
import com.intellij.polySymbols.query.PolySymbolScopePartialMatchingSupportBuilder
import com.intellij.polySymbols.query.PolySymbolScopePartialMatchingSupportBuilderBase
import com.intellij.polySymbols.utils.PolySymbolScopeWithCache
import com.intellij.polySymbols.utils.ReferencingPolySymbol
import com.intellij.polySymbols.query.ProjectPolySymbolScopeCachedBuilder
import com.intellij.polySymbols.query.ProjectPolySymbolScopeCachedInitializer
import com.intellij.polySymbols.query.ProjectPolySymbolScopePartialMatchingSupportBuilder
import com.intellij.polySymbols.query.PsiPolySymbolScopeCachedBuilder
import com.intellij.polySymbols.query.PsiPolySymbolScopeCachedInitializer
import com.intellij.polySymbols.query.PsiPolySymbolScopePartialMatchingSupportBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.util.concurrent.ConcurrentHashMap

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

// ─── partialMatchingSupport collectors ─────────────────────────────────────────

/**
 * Collected result of one `partialMatchingSupport { }` block evaluation - a plain data holder, not
 * part of the public DSL surface (callers only ever interact via `provideMatchingSymbols(...)`).
 * `internal`, not `private`, only because it - and [PartialMatchingSupportCollectorBase] below - are
 * referenced from [BuiltPolySymbolScopeWithCache]'s constructor parameter types, and Kotlin requires
 * a declaration's exposed parameter types to be at least as visible as the declaration itself.
 */
internal class FixedPolySymbolScopePartialMatchingSupport(
  val cacheDependencies: Collection<Any>,
  val lookup: (kind: PolySymbolKind, nameVariant: String) -> List<PolySymbol>,
)

internal abstract class PartialMatchingSupportCollectorBase<K>(
  override val project: Project,
  override val key: K,
) : PolySymbolScopePartialMatchingSupportBuilderBase<K> {

  var support: FixedPolySymbolScopePartialMatchingSupport? = null
    private set

  final override fun provideMatchingSymbols(
    cacheDependencies: Collection<Any>,
    lookup: (kind: PolySymbolKind, nameVariant: String) -> List<PolySymbol>,
  ) {
    check(support == null) { "polySymbolScopeCached: partialMatchingSupport { provideMatchingSymbols(...) } must be called at most once." }
    check(cacheDependencies.isNotEmpty()) { "polySymbolScopeCached: provideMatchingSymbols cacheDependencies must not be empty." }
    support = FixedPolySymbolScopePartialMatchingSupport(cacheDependencies, lookup)
  }

  final override fun provideMatchingSymbols(
    vararg cacheDependencies: Any,
    lookup: (kind: PolySymbolKind, nameVariant: String) -> List<PolySymbol>,
  ) {
    provideMatchingSymbols(cacheDependencies.toList(), lookup)
  }
}

private class ProjectPartialMatchingSupportCollector<K>(
  project: Project,
  key: K,
) : PartialMatchingSupportCollectorBase<K>(project, key), ProjectPolySymbolScopePartialMatchingSupportBuilder<K>

private class PsiPartialMatchingSupportCollector<T : PsiElement, K>(
  project: Project,
  override val element: T,
  key: K,
) : PartialMatchingSupportCollectorBase<K>(project, key), PsiPolySymbolScopePartialMatchingSupportBuilder<T, K>

private class UserDataHolderPartialMatchingSupportCollector<T : UserDataHolder, K>(
  project: Project,
  override val dataHolder: T,
  key: K,
) : PartialMatchingSupportCollectorBase<K>(project, key), PolySymbolScopePartialMatchingSupportBuilder<T, K>

// ─── Builders ───────────────────────────────────────────────────────────────────

internal class ProjectPolySymbolScopeCachedBuilderImpl<K>(
  project: Project,
  key: K,
  private val configure: ProjectPolySymbolScopeCachedBuilder<K>.() -> Unit,
) : AbstractBuilder<K>(project, key), ProjectPolySymbolScopeCachedBuilder<K> {

  init {
    checkNoPsiCapture(configure, "polySymbolScopeCached.configure")
  }

  private var initBody: (ProjectPolySymbolScopeCachedInitializer<K>.() -> Unit)? = null
  private var partialMatchingSupportConfigure: (ProjectPolySymbolScopePartialMatchingSupportBuilder<K>.() -> Unit)? = null
  private var partialMatchingSupportDecisionCacheDependencies: Collection<Any>? = null

  override fun initialize(body: ProjectPolySymbolScopeCachedInitializer<K>.() -> Unit) {
    check(initBody == null) { "polySymbolScopeCached: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolScopeCached.initialize")
    initBody = body
  }

  override fun partialMatchingSupport(configure: ProjectPolySymbolScopePartialMatchingSupportBuilder<K>.() -> Unit) {
    check(partialMatchingSupportConfigure == null) { "polySymbolScopeCached: partialMatchingSupport { } must be called at most once." }
    checkNoPsiCapture(configure, "polySymbolScopeCached.partialMatchingSupport")
    partialMatchingSupportConfigure = configure
  }

  override fun partialMatchingSupport(
    cacheDependencies: Collection<Any>,
    configure: ProjectPolySymbolScopePartialMatchingSupportBuilder<K>.() -> Unit,
  ) {
    check(partialMatchingSupportConfigure == null) { "polySymbolScopeCached: partialMatchingSupport { } must be called at most once." }
    check(cacheDependencies.isNotEmpty()) { "polySymbolScopeCached: partialMatchingSupport cacheDependencies must not be empty." }
    checkNoPsiCapture(configure, "polySymbolScopeCached.partialMatchingSupport")
    partialMatchingSupportDecisionCacheDependencies = cacheDependencies
    partialMatchingSupportConfigure = configure
  }

  override fun partialMatchingSupport(
    vararg cacheDependencies: Any,
    configure: ProjectPolySymbolScopePartialMatchingSupportBuilder<K>.() -> Unit,
  ) {
    partialMatchingSupport(cacheDependencies.toList(), configure)
  }

  fun build(): BuiltPolySymbolScopeWithCache<Project, K> {
    configure(this)
    val body = initBody ?: error("polySymbolScopeCached: initialize { } was not called.")
    check(providesKinds.isNotEmpty()) { "polySymbolScopeCached: provides() must be called with at least one kind." }
    val projectRef = project
    val keyRef = key
    val configureRef = configure
    val partialConfigure = partialMatchingSupportConfigure
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
      partialMatchingSupportDecisionCacheDependencies = partialMatchingSupportDecisionCacheDependencies,
      partialMatchingSupportBody = partialConfigure?.let { partialBody ->
        {
          @Suppress("UNCHECKED_CAST")
          partialBody.invoke(this as ProjectPolySymbolScopePartialMatchingSupportBuilder<K>)
        }
      },
      partialMatchingSupportCollectorFactory = { snapshotProject, _, snapshotKey ->
        ProjectPartialMatchingSupportCollector(snapshotProject, snapshotKey)
      },
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
  private var partialMatchingSupportConfigure: (PsiPolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit)? = null
  private var partialMatchingSupportDecisionCacheDependencies: Collection<Any>? = null

  override fun initialize(body: PsiPolySymbolScopeCachedInitializer<T, K>.() -> Unit) {
    check(initBody == null) { "polySymbolScopeCached: initialize { } must be called exactly once." }
    checkNoPsiCapture(body, "polySymbolScopeCached.initialize")
    initBody = body
  }

  override fun partialMatchingSupport(configure: PsiPolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit) {
    check(partialMatchingSupportConfigure == null) { "polySymbolScopeCached: partialMatchingSupport { } must be called at most once." }
    checkNoPsiCapture(configure, "polySymbolScopeCached.partialMatchingSupport")
    partialMatchingSupportConfigure = configure
  }

  override fun partialMatchingSupport(
    cacheDependencies: Collection<Any>,
    configure: PsiPolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit,
  ) {
    check(partialMatchingSupportConfigure == null) { "polySymbolScopeCached: partialMatchingSupport { } must be called at most once." }
    check(cacheDependencies.isNotEmpty()) { "polySymbolScopeCached: partialMatchingSupport cacheDependencies must not be empty." }
    checkNoPsiCapture(configure, "polySymbolScopeCached.partialMatchingSupport")
    partialMatchingSupportDecisionCacheDependencies = cacheDependencies
    partialMatchingSupportConfigure = configure
  }

  override fun partialMatchingSupport(
    vararg cacheDependencies: Any,
    configure: PsiPolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit,
  ) {
    partialMatchingSupport(cacheDependencies.toList(), configure)
  }

  fun build(): BuiltPolySymbolScopeWithCache<T, K> {
    configure(this)
    val body = initBody ?: error("polySymbolScopeCached: initialize { } was not called.")
    check(providesKinds.isNotEmpty()) { "polySymbolScopeCached: provides() must be called with at least one kind." }
    val keyRef = key
    val configureRef = configure
    val partialConfigure = partialMatchingSupportConfigure
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
      partialMatchingSupportDecisionCacheDependencies = partialMatchingSupportDecisionCacheDependencies,
      partialMatchingSupportBody = partialConfigure?.let { partialBody ->
        {
          @Suppress("UNCHECKED_CAST")
          partialBody.invoke(this as PsiPolySymbolScopePartialMatchingSupportBuilder<T, K>)
        }
      },
      partialMatchingSupportCollectorFactory = { snapshotProject, snapshotHolder, snapshotKey ->
        PsiPartialMatchingSupportCollector(snapshotProject, snapshotHolder, snapshotKey)
      },
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
  private var partialMatchingSupportConfigure: (PolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit)? = null
  private var partialMatchingSupportDecisionCacheDependencies: Collection<Any>? = null

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

  override fun partialMatchingSupport(configure: PolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit) {
    check(partialMatchingSupportConfigure == null) { "polySymbolScopeCached: partialMatchingSupport { } must be called at most once." }
    checkNoPsiCapture(configure, "polySymbolScopeCached.partialMatchingSupport")
    partialMatchingSupportConfigure = configure
  }

  override fun partialMatchingSupport(
    cacheDependencies: Collection<Any>,
    configure: PolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit,
  ) {
    check(partialMatchingSupportConfigure == null) { "polySymbolScopeCached: partialMatchingSupport { } must be called at most once." }
    check(cacheDependencies.isNotEmpty()) { "polySymbolScopeCached: partialMatchingSupport cacheDependencies must not be empty." }
    checkNoPsiCapture(configure, "polySymbolScopeCached.partialMatchingSupport")
    partialMatchingSupportDecisionCacheDependencies = cacheDependencies
    partialMatchingSupportConfigure = configure
  }

  override fun partialMatchingSupport(
    vararg cacheDependencies: Any,
    configure: PolySymbolScopePartialMatchingSupportBuilder<T, K>.() -> Unit,
  ) {
    partialMatchingSupport(cacheDependencies.toList(), configure)
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
    val partialConfigure = partialMatchingSupportConfigure
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
      partialMatchingSupportDecisionCacheDependencies = partialMatchingSupportDecisionCacheDependencies,
      partialMatchingSupportBody = partialConfigure?.let { partialBody ->
        {
          @Suppress("UNCHECKED_CAST")
          partialBody.invoke(this as PolySymbolScopePartialMatchingSupportBuilder<T, K>)
        }
      },
      partialMatchingSupportCollectorFactory = { snapshotProject, snapshotHolder, snapshotKey ->
        UserDataHolderPartialMatchingSupportCollector(snapshotProject, snapshotHolder, snapshotKey)
      },
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

/** Dedicated userData-map marker for the partial-matching-support decision cache - kept distinct
 *  from [PolySymbolScopeWithCache]'s own internal search-map cache key so the two never collide on
 *  the same `dataHolder`. */
private object PartialMatchingSupportDecisionMapMarker

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
  private val partialMatchingSupportDecisionCacheDependencies: Collection<Any>?,
  private val partialMatchingSupportBody: (PolySymbolScopePartialMatchingSupportBuilderBase<K>.() -> Unit)?,
  private val partialMatchingSupportCollectorFactory: (Project, T, K) -> PartialMatchingSupportCollectorBase<K>,
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

  private fun runPartialMatchingSupportCollector(): FixedPolySymbolScopePartialMatchingSupport? {
    val body = partialMatchingSupportBody ?: return null
    val collector = partialMatchingSupportCollectorFactory(project, dataHolder, userKey)
    body.invoke(collector)
    return collector.support
  }

  // Only ever read from the no-argument-`partialMatchingSupport { }`-configured branch below - the
  // initializer therefore only ever runs (at most once, for this scope instance's lifetime) when
  // that branch is actually taken; no CachedValuesManager involved on this path at all.
  private val partialMatchingSupportLazyValue: FixedPolySymbolScopePartialMatchingSupport? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    runPartialMatchingSupportCollector()
  }

  private fun getCachedPartialMatchingSupportDecision(decisionCacheDependencies: Collection<Any>): FixedPolySymbolScopePartialMatchingSupport? {
    val manager = CachedValuesManager.getManager(project)
    val perScopeMap: ConcurrentHashMap<Pair<Class<*>, K>, CachedValue<FixedPolySymbolScopePartialMatchingSupport?>> =
      manager.getCachedValue(dataHolder, manager.getKeyForClass(PartialMatchingSupportDecisionMapMarker::class.java), {
        CachedValueProvider.Result(ConcurrentHashMap(), ModificationTracker.NEVER_CHANGED)
      }, false)
    val cachedValue = perScopeMap.getOrPut(key) {
      manager.createCachedValue {
        CachedValueProvider.Result.create(runPartialMatchingSupportCollector(), decisionCacheDependencies.toList())
      }
    }
    return cachedValue.value
  }

  override val partialMatchingSupport: PartialMatchingSupport?
    get() {
      val decisionDeps = partialMatchingSupportDecisionCacheDependencies
      val fixedSupport =
        if (partialMatchingSupportBody == null) null
        else if (decisionDeps == null) partialMatchingSupportLazyValue
        else getCachedPartialMatchingSupportDecision(decisionDeps)
      return fixedSupport?.let { fixed ->
        object : PartialMatchingSupport {
          override val cacheDependencies: Collection<Any> = fixed.cacheDependencies
          override fun getMatchingSymbols(kind: PolySymbolKind, nameVariant: String): List<PolySymbol> =
            fixed.lookup(kind, nameVariant)
        }
      }
    }

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
