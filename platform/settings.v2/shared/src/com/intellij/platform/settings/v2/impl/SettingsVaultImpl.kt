// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/change-listeners.spec.md
// @spec ../../../../../../../../spec/key-registry.spec.md
// @spec ../../../../../../../../spec/layer-loading.spec.md
// @spec ../../../../../../../../spec/layer-locality.spec.md
// @spec ../../../../../../../../spec/layer-resolution.spec.md
// @spec ../../../../../../../../spec/multidimensional-settings.spec.md
// @spec ../../../../../../../../spec/settings-writes.spec.md
// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.settings.v2.ContextFactory
import com.intellij.platform.settings.v2.EDIT_TEAM_LAYER
import com.intellij.platform.settings.v2.KeyLocality
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.ReadOnlySettingsException
import com.intellij.platform.settings.v2.SaveResult
import com.intellij.platform.settings.v2.SettingsChangeListener
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMapKey
import com.intellij.platform.settings.v2.SettingsMutationBuilder
import com.intellij.platform.settings.v2.SettingsValidationException
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.cache.CachedLayer
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheStorage
import com.intellij.platform.settings.v2.impl.cache.SettingsSnapshotCache
import com.intellij.platform.settings.v2.impl.cache.isSnapshotCacheEnabled
import com.intellij.platform.settings.v2.impl.providers.AppDefaultsLayerProvider
import com.intellij.platform.settings.v2.impl.providers.AppLayerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holds the settings layer data as the single source of truth (ADR 0057). The vault owns the in-memory model,
 * `context.identity -> ((locality, provider) -> `[MapValue]`)`, and all the mutation logic. A provider is only a
 * persistence port ([LayerProvider.availableLocalities], [LayerProvider.writeData]).
 *
 * **A read takes no lock.** The model is an immutable snapshot in a `@Volatile` reference, and a read only takes the
 * reference (ADR 0136). **The writers serialize on one [Mutex]** and swap in a new snapshot (copy-on-write). So a read
 * always sees a consistent whole, and a throw during the compute leaves the model as it was. A mutation suspends and
 * applies under the lock, so the caller reads its own write after the call (ADR 0058).
 *
 * **The vault drives the persistence, after a delay.** A mutation schedules a debounced save with no payload for each
 * storage unit that it touches (ADR 0061). When the save fires, the vault reads the current slice of the model under
 * the lock and gives it to [LayerProvider.writeData]. While a save is pending, the vault drops the spontaneous
 * [push]es of that store. This also drops the watcher echo of the own write of the vault.
 */
internal class SettingsVaultImpl(private val scope: CoroutineScope) : SettingsVault {
  /** Holds the model: an immutable snapshot that a writer swaps under [vaultWriteMutex]. A read takes it with no lock. */
  @Volatile
  private var model: Map<String, ContextLayers> = emptyMap()

  /** Serializes the writers: the mutations, the reload installs and the [push] folds. A reader never takes it. */
  private val vaultWriteMutex = Mutex()

  /**
   * Holds the change watches. The vault notifies them after a model swap. A watch reacts when one of its
   * [dependency identities][SettingsKeyWatch.dependencyKeys] shows up in a changed layer.
   */
  private val mySettingsKeyWatches = ConcurrentHashMap.newKeySet<SettingsKeyWatch>()

  /**
   * Guards [queuedNotifications], the [current values][SettingsKeyWatch.currentValue] of the contextual watches, the
   * removal of a watch, and the add of an entry to [unfinishedReloads]. The vault never calls a listener under it, and
   * it never suspends under it.
   */
  private val notificationLock = Any()

  /**
   * Holds the listener notifications that wait until the context they carry is ready (ADR 0161). A key-only event
   * carries the context of its change. A contextual watch carries its own context. Each contextual watch and each
   * key-only event is in the queue once, so the queue folds several changes into one notification. Guarded by
   * [notificationLock].
   *
   * These rules make the queue race-free:
   * - Every notification goes into the queue. In the same [notificationLock] hold, [takeReadyNotifications] takes out
   *   each notification whose context is ready.
   * - A context gets dirty only when a reload goes into [unfinishedReloads], under [notificationLock]. So a context that
   *   the take finds ready stays ready during the hold. A contextual watch resolves its value there.
   * - A context gets ready only in [swapInReloadProgress], and a [notifyListeners] call always comes after it. That call
   *   takes out a notification that was in the queue before the change. A notification that goes in after the change
   *   finds the context ready in its own call.
   * - A watch leaves the queue when it is disposed, under [notificationLock]. A watch that is not registered does not
   *   go into the queue.
   */
  private val queuedNotifications = SettingsChangeEvents()

  /**
   * Queues the spontaneous pushes of the layers, such as a watcher that reacts to an external edit. A single consumer
   * folds them into the model, so concurrent pushes serialize. The channel is unbounded, so [push] never blocks.
   */
  private val incoming = Channel<LayerData>(Channel.UNLIMITED)

  /**
   * Holds one debounced saver per storage unit, keyed by `(storageContext.identity, coordinate)`. Each channel is
   * conflated, so repeated save signals coalesce.
   */
  private val savingChannels = ConcurrentHashMap<Pair<String, LayerCoordinate>, Channel<Unit>>()

  /**
   * Holds one write mutex per storage unit. The debounced saver and a forced [flush] both persist a unit through
   * [persistNow]. The mutex keeps the two from writing the same file at once. The first one persists, and the other
   * one finds the unit no longer pending and skips it.
   */
  private val writeMutexes = ConcurrentHashMap<Pair<String, LayerCoordinate>, Mutex>()

  /**
   * Holds the storage units whose applied mutation is not persisted yet. In this window the file on the disk is stale.
   * So the vault drops a spontaneous [push] for such a unit, because the vault is the truth. [applyMutations] marks a
   * unit together with the mutation, and [persistNow] clears the mark when the write completes. Guarded by
   * [vaultWriteMutex].
   */
  private val pendingSaves = HashSet<Pair<String, LayerCoordinate>>()

  /**
   * Holds the pushes whose store is in a dirty context, in arrival order. The vault applies them when the context of
   * their store gets ready (ADR 0123, ADR 0160). See [swapInReloadProgress]. Guarded by [vaultWriteMutex].
   */
  private val heldPushes = ArrayList<LayerData>()

  /**
   * Holds the requested reloads that have not finished, the running ones and the queued ones. Each one keeps its scope
   * and its [pending kinds][UnfinishedReload.pendingKinds] (ADR 0160). The same or overlapping scopes can be in it at
   * once. [reloadScope] adds the entry before it waits for [reloadMutexes], so a queued reload already makes its
   * contexts dirty. There is no gap between the end of one reload and the start of the next one. [reloadScope] adds the
   * entry under [notificationLock], because the add makes contexts dirty (see [queuedNotifications]). [isDirty]
   * iterates it with no lock.
   */
  private val unfinishedReloads = CopyOnWriteArrayList<UnfinishedReload>()

  /**
   * Holds one reload mutex per context kind (ADR 0162). Every reload takes all of them, whatever its scope, from
   * [SettingsContextKind.APPLICATION] down to [SettingsContextKind.FILE]. So the asks and the installs of one kind keep
   * the order of the reloads. A slow reload cannot install last and overwrite a newer one with the stale data of its
   * ask. A mutex blocks only other reloads, not reads or mutations. See [installKindByKind].
   */
  private val reloadMutexes: Map<SettingsContextKind, Mutex> = SettingsContextKind.entries.associateWith { Mutex() }

  /**
   * Holds a counter that grows on every readiness change: a reload that [unfinishedReloads] gets or drops, and a
   * change of the [pending kinds][UnfinishedReload.pendingKinds] of a reload. So [awaitReady] can suspend until a
   * context stops being dirty. Each value is new, so a `StateFlow` collector always gets it. The counter grows after
   * the change, so a new check sees the updated state.
   */
  private val loadingGeneration = MutableStateFlow(0)

  /**
   * Emits after every model swap, so the snapshot cache can mark itself dirty. The `Unit` value means that something
   * changed somewhere. A cache dumps its scope again and skips the write when the dump did not change.
   * [SettingsSnapshotCache] consumes it, for the application and for each project.
   */
  private val modelChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /** Exposes [modelChanged] to [SettingsSnapshotCache.startSaving]. */
  internal val modelChangedSignal: SharedFlow<Unit> get() = modelChanged

  /**
   * Holds the snapshot cache of the application scope. It seeds the model at startup, before the boot reload, and
   * then persists it. It is `null` when the cache is off ([isSnapshotCacheEnabled]), and then every cache operation
   * does nothing.
   */
  private val appCache = if (isSnapshotCacheEnabled()) SettingsSnapshotCache(SettingsCacheStorage.appCacheFile(), project = null) else null

  init {
    scope.launch {
      for (layerData in incoming) processPendingPushes(layerData)
    }
    LayerProvider.EP_NAME.addExtensionPointListener(scope, object : ExtensionPointListener<LayerProvider> {
      override fun extensionAdded(extension: LayerProvider, pluginDescriptor: PluginDescriptor) {
        assertBaseLayerOrder()
        scope.launch { loadFrom(extension, bootScope()) }
      }

      override fun extensionRemoved(extension: LayerProvider, pluginDescriptor: PluginDescriptor) {
        scope.launch { forgetProvider(extension) }
        assertBaseLayerOrder()
      }
    })
    assertBaseLayerOrder()
    scope.launch {
      // Seed from the snapshot cache first, then reload the real providers to reconcile. The order is not critical,
      // because installCached never overwrites a layer that a reload installed. But an early seed is the purpose of the cache.
      appCache?.let {
        it.load()
        it.startSaving(scope)
      }
      reload(bootScope())
    }
  }

  /** Writes the application snapshot cache if it has pending changes. [SettingsAppLifecycleListener] calls it at the close. */
  internal suspend fun saveAppCacheIfDirty() {
    appCache?.saveIfDirty()
  }

  override fun isDirty(context: SettingsContext): Boolean =
    unfinishedReloads.any { it.keepsDirty(context) }

  override suspend fun awaitReady(context: SettingsContext) {
    // Re-checks on the current value and on every [loadingGeneration] bump; returns once no reload keeps [context] dirty.
    loadingGeneration.first { !isDirty(context) }
  }

  /**
   * Suspends until all [contexts] are ready at the same moment. A loop that awaits each context in turn is not the
   * same: an earlier context can turn dirty again while the loop waits for a later one.
   */
  private suspend fun awaitAllReady(contexts: List<SettingsContext>) {
    loadingGeneration.first { contexts.none { isDirty(it) } }
  }

  override suspend fun reload(scopeContext: SettingsContext) {
    reloadScope(storageScope(scopeContext))
  }

  /**
   * Returns the widest storage context that any provider maps [scopeContext] to, or [scopeContext] itself when none is
   * wider. A reload then covers every store it touches as a whole (ADR 0115).
   */
  private fun storageScope(scopeContext: SettingsContext): SettingsContext =
    LayerProvider.EP_NAME.extensionList
      .map { it.storageContext(scopeContext) }
      .fold(scopeContext) { widest, storageContext -> if (storageContext.isAncestorOf(widest, strict = true)) storageContext else widest }

  /**
   * Reloads [scopeContext] kind by kind (ADR 0160, ADR 0162). See [installKindByKind]. On a failure or a cancellation
   * of the caller, the reload releases every reload mutex that it holds, and every pending kind stops being pending.
   */
  private suspend fun reloadScope(scopeContext: SettingsContext) {
    // Mark the scope dirty before the wait for the reload mutexes. See unfinishedReloads.
    val reload = UnfinishedReload(scopeContext)
    synchronized(notificationLock) { unfinishedReloads.add(reload) }
    loadingGeneration.update { it + 1 }
    val heldReloadMutexes = HeldReloadMutexes(reloadMutexes)
    try {
      coroutineScope { installKindByKind(reload, heldReloadMutexes) }
    }
    finally {
      withContext(NonCancellable) {
        heldReloadMutexes.unlockAll()
        finishReload(reload)
      }
    }
  }

  /**
   * Takes the [reloadMutexes] hand over hand, from the widest kind to the narrowest kind (ADR 0162). It takes the
   * mutex of the next kind as soon as it can, before it releases the mutex of the current kind.
   *
   * - It asks a provider as soon as it holds the mutex of the widest kind of the provider. So a reload that no other
   *   reload blocks asks every provider at once.
   * - It installs the bucket of a kind while it holds the mutex of that kind (ADR 0160). See [installBucket].
   * - It releases the mutex of a kind after the install of its bucket, once it holds the next mutex. The mutex of the
   *   narrowest kind stays held until [reloadScope] ends.
   *
   * @param heldReloadMutexes the reload mutexes that this reload holds, which [reloadScope] releases at the end
   */
  private suspend fun CoroutineScope.installKindByKind(reload: UnfinishedReload, heldReloadMutexes: HeldReloadMutexes) {
    val providers = LayerProvider.EP_NAME.extensionList
    val answers = HashMap<LayerProvider, Deferred<List<LayerData>?>>()
    var widerBucket: Pair<SettingsContextKind, Job>? = null
    for (kind in SettingsContextKind.entries) {
      heldReloadMutexes.lock(kind)
      widerBucket?.let { (widerKind, bucketInstall) ->
        launch {
          bucketInstall.join()
          heldReloadMutexes.unlock(widerKind)
        }
      }
      for (provider in providers) {
        if (provider.contextKindRange.widestKind == kind) answers[provider] = async { requestDataOrNull(provider, reload.scopeContext) }
      }
      val bucketAnswers = providers.filter { kind in it.contextKindRange }.map { it to answers.getValue(it) }
      widerBucket = kind to launch { installBucket(reload, kind, bucketAnswers.map { (provider, answer) -> provider to answer.await() }) }
    }
  }

  /**
   * Ends [reload], also a failed or a cancelled one. Every kind that it has not installed stops being pending, so its
   * contexts get ready. The held pushes of the stores that get ready apply in the same [vaultWriteMutex] hold. See
   * [swapInReloadProgress].
   */
  private suspend fun finishReload(reload: UnfinishedReload) {
    val settingsChangeEvents = SettingsChangeEvents()
    vaultWriteMutex.withLock {
      swapInReloadProgress(model, reload, pendingKinds = emptySet(), settingsChangeEvents)
      unfinishedReloads.remove(reload)
    }
    loadingGeneration.update { it + 1 }
    notifyListeners(settingsChangeEvents)
  }

  /**
   * Swaps in [currentModel] with the [heldPushes] that get ready, then sets the pending kinds of [reload]. A held push
   * gets ready when the context of its store is not dirty with the new pending kinds. These pushes apply in arrival
   * order. So a context gets ready only after its held pushes are in the model, and a new push cannot apply before a
   * held push of the same store (ADR 0160). Call under [vaultWriteMutex]. After the hold, bump [loadingGeneration] and
   * call [notifyListeners], so the queued notifications of the contexts that get ready deliver (ADR 0161).
   *
   * @param pendingKinds the new [pending kinds][UnfinishedReload.pendingKinds] of [reload]
   */
  private fun swapInReloadProgress(
    currentModel: Map<String, ContextLayers>,
    reload: UnfinishedReload,
    pendingKinds: Set<SettingsContextKind>,
    settingsChangeEvents: SettingsChangeEvents,
  ) {
    var modelWithPushes = currentModel
    val iterator = heldPushes.iterator()
    while (iterator.hasNext()) {
      val layerData = iterator.next()
      val storageContext = storageContextOf(layerData)
      val staysDirty = unfinishedReloads.any { it.keepsDirty(storageContext, if (it === reload) pendingKinds else it.pendingKinds) }
      if (staysDirty) continue
      iterator.remove()
      modelWithPushes = processPush(modelWithPushes, layerData, settingsChangeEvents)
    }
    model = modelWithPushes
    reload.pendingKinds = pendingKinds
  }

  /** Returns whether the context of the store of [layerData] is [dirty][isDirty]. Then the vault holds a push of it. */
  private fun isStoreDirty(layerData: LayerData): Boolean = isDirty(storageContextOf(layerData))

  /** Returns the context that owns the store of [layerData]. See [LayerProvider.storageContext]. */
  private fun storageContextOf(layerData: LayerData): SettingsContext = layerData.provider.storageContext(layerData.context)

  /**
   * Returns the data of [provider] for [scopeContext], or `null` if the provider fails. The failure is logged. The
   * vault isolates a failing provider and does not sweep its entries.
   */
  private suspend fun requestDataOrNull(provider: LayerProvider, scopeContext: SettingsContext): List<LayerData>? =
    try {
      requestLayersInKindRange(provider, scopeContext)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.error("Failed to load settings from '${provider.javaClass.name}' for scope '${scopeContext.identity}'", e)
      null
    }

  /**
   * Installs the bucket of [kind] of [reload] (ADR 0160). It installs only the returned layers at [kind], then sweeps
   * the stale layers of [kind] under the scope (ADR 0065). A stale layer is in the model, and a returning provider did
   * not supply it again. A layer with a pending save is not stale, because an unpersisted mutation must survive until
   * the disk catches up. The install, the sweep and the readiness change run under one [vaultWriteMutex] hold, so the
   * bucket swaps in atomically, and the change events deliver once. The caller holds the reload mutex of [kind]
   * (ADR 0162).
   *
   * @param answers the answer of each provider whose range of context kinds contains [kind], or `null` for a provider
   *   that failed. The vault does not sweep a failed provider.
   */
  private suspend fun installBucket(
    reload: UnfinishedReload,
    kind: SettingsContextKind,
    answers: List<Pair<LayerProvider, List<LayerData>?>>,
  ) {
    val settingsChangeEvents = SettingsChangeEvents()
    vaultWriteMutex.withLock {
      var currentModel = model
      val suppliedLayers = HashSet<Pair<String, LayerCoordinate>>()
      for ((provider, layersData) in answers) {
        for (layerData in layersData ?: continue) {
          if (layerData.context.kind != kind) continue
          suppliedLayers.add(layerData.context.identity to LayerCoordinate(provider, layerData.locality))
          currentModel = processPush(currentModel, layerData, settingsChangeEvents)
        }
      }
      val returningProviders = answers.mapNotNullTo(HashSet()) { (provider, layers) -> provider.takeIf { layers != null } }
      currentModel = sweepStale(currentModel, reload.scopeContext, kind, returningProviders, suppliedLayers, settingsChangeEvents)
      swapInReloadProgress(currentModel, reload, reload.pendingKinds - kind, settingsChangeEvents)
    }
    loadingGeneration.update { it + 1 }
    notifyListeners(settingsChangeEvents)
  }

  /**
   * Drops the entries of [kind] of each returning provider under [scopeContext] that it did not supply again and that
   * have no pending save. A provider returns when its reload does not fail. A failed provider gave `null`, and the
   * vault does not sweep it. The supply check and the pending-save check work per [LayerCoordinate], so the vault can
   * sweep one locality of a provider and keep another one. See [installBucket]. Call under [vaultWriteMutex].
   */
  private fun sweepStale(
    model: Map<String, ContextLayers>,
    scopeContext: SettingsContext,
    kind: SettingsContextKind,
    returningProviders: Set<LayerProvider>,
    suppliedLayers: Set<Pair<String, LayerCoordinate>>,
    settingsChangeEvents: SettingsChangeEvents,
  ): Map<String, ContextLayers> {
    val stale = model.values.filter { it.context.kind == kind && scopeContext.isAncestorOf(it.context, strict = false) }.flatMap { entry ->
      entry.layers.keys.filter { coordinate ->
        coordinate.provider in returningProviders &&
        (entry.context.identity to coordinate) !in suppliedLayers &&
        (coordinate.provider.storageContext(entry.context).identity to coordinate) !in pendingSaves
      }.map { entry.context to it }
    }
    var currentModel = model
    for ((context, coordinate) in stale) {
      val layer = currentModel[context.identity]?.layers?.get(coordinate) ?: continue
      collectChangeEvents(context, layer, null, settingsChangeEvents)
      currentModel = currentModel.without(context, coordinate)
    }
    return currentModel
  }

  /**
   * Returns the layers that [provider] returns for [scopeContext], without the layers outside its range of context
   * kinds. See [isInKindRange].
   */
  private suspend fun requestLayersInKindRange(provider: LayerProvider, scopeContext: SettingsContext): List<LayerData> =
    provider.requestData(scopeContext).filter(::isInKindRange)

  /**
   * Returns whether the context of [layerData] has a kind in the [range of context kinds][LayerProvider.contextKindRange]
   * of its provider. It logs an error for a layer outside the range, and the caller drops that layer (ADR 0159).
   */
  private fun isInKindRange(layerData: LayerData): Boolean {
    val kindRange = layerData.provider.contextKindRange
    val kind = layerData.context.kind
    if (kind in kindRange) return true
    LOG.error("Dropping a layer of '${layerData.provider.javaClass.name}' at '${layerData.context.identity}': " +
              "its kind $kind is outside the range $kindRange of the provider")
    return false
  }

  /** Asks [provider] for its [scopeContext] data and installs it before it returns. A load is synchronous, a spontaneous [push] is not. */
  private suspend fun loadFrom(provider: LayerProvider, scopeContext: SettingsContext) {
    installLoaded(requestLayersInKindRange(provider, scopeContext))
  }

  /**
   * Folds a batch of loaded [layers] into the model under one lock hold and delivers the change events once. It is the
   * synchronous counterpart of [processPendingPushes], so the [loadFrom] caller sees the data installed on return. It
   * uses [processPush], so a load that matches the model changes nothing, and a pending save drops a load the same way
   * as a spontaneous push.
   */
  private suspend fun installLoaded(layers: List<LayerData>) {
    if (layers.isEmpty()) return
    val settingsChangeEvents = SettingsChangeEvents()
    vaultWriteMutex.withLock {
      var currentModel = model
      for (layerData in layers) currentModel = processPush(currentModel, layerData, settingsChangeEvents)
      model = currentModel
    }
    notifyListeners(settingsChangeEvents)
  }

  /** Returns the widest scope that the vault loads at startup and when a provider is added. */
  private fun bootScope(): SettingsContext = AppSettingsContext

  /**
   * Checks that the two base layers keep their fixed order: the application defaults first, then the writable
   * application layer. The check skips an absent base layer, so a test that masks the EP with its own providers passes.
   */
  private fun assertBaseLayerOrder() {
    val providers = LayerProvider.EP_NAME.extensionList
    providers.indexOfFirst { it is AppDefaultsLayerProvider }
      .let { check(it < 0 || it == 0) { "AppDefaultsLayerProvider must be the first layer, was at $it" } }
    providers.indexOfFirst { it is AppLayerProvider }
      .let { check(it < 0 || it == 1) { "AppLayerProvider must be the second layer, was at $it" } }
  }

  /**
   * Drops every structure keyed by [provider] when its extension unloads: its cached layers, its pending-save
   * markers, its held pushes, and its saver coroutine (closing the channel ends the loop). Otherwise the coroutine and
   * a reference to the unloaded provider would leak.
   */
  private suspend fun forgetProvider(provider: LayerProvider) {
    savingChannels.keys.filter { it.second.provider == provider }.forEach { savingChannels.remove(it)?.close() }
    val settingsChangeEvents = SettingsChangeEvents()
    vaultWriteMutex.withLock {
      for (contextLayers in model.values) {
        for ((coordinate, data) in contextLayers.layers) {
          if (coordinate.provider == provider) collectChangeEvents(contextLayers.context, data, null, settingsChangeEvents)
        }
      }
      model = model.mapValues { it.value.without(provider) }.filterValues { it.layers.isNotEmpty() }
      pendingSaves.removeAll { it.second.provider == provider }
      heldPushes.removeAll { it.provider == provider }
    }
    notifyListeners(settingsChangeEvents)
  }

  override fun snapshot(context: SettingsContext, key: SettingsKey<*>): List<MapValue> =
    generateSequence(context) { it.parentContext }.filter { key.contextFilter(it) }.toList().flatMap { layersOf(it, key) }

  /**
   * Returns the layer maps of this one [context] that [key] resolves against, narrowest first. The locality precedence
   * orders them first ([LayerLocality.byResolutionPrecedence]: environmental, local, team). Within a locality, the
   * providers come narrowest first. Only the localities that the [KeyLocality][SettingsKey.locality] of the key accepts
   * and the [candidate providers][isCandidate] take part. The list is empty when the model holds nothing for the context.
   */
  private fun layersOf(context: SettingsContext, key: SettingsKey<*>): List<MapValue> {
    val perCoordinate = model[context.identity]?.layers ?: return emptyList()
    val providersNarrowestFirst = LayerProvider.EP_NAME.extensionList.filter { isCandidate(it, key) }.asReversed()
    return LayerLocality.byResolutionPrecedence
      .filter { key.locality.accepts(it) }
      .flatMap { locality -> providersNarrowestFirst.mapNotNull { provider -> perCoordinate[LayerCoordinate(provider, locality)] } }
  }

  /**
   * Returns whether the layers of [provider] exist for [key]. The key and the provider filter each other: the
   * [layer filter][SettingsKey.layerFilter] of the key must accept the provider, and the [key filter][LayerProvider.acceptsKey]
   * of the provider must accept the key. A read, a write and a slot operation all use this one check (ADR 0019, ADR 0129).
   */
  private fun isCandidate(provider: LayerProvider, key: SettingsKey<*>): Boolean =
    key.layerFilter(provider) && provider.acceptsKey(key)

  override fun push(data: LayerData) {
    if (isInKindRange(data)) incoming.trySend(data)
  }

  override suspend fun <T> set(context: SettingsContext, key: SettingsKey<T>, value: T) {
    applyMutations(listOf(SetMutation(context, key, value)))
  }

  override suspend fun copySlot(context: SettingsContext, mapKey: SettingsMapKey, from: String, to: String) {
    applyMutations(listOf(CopySlotMutation(context, mapKey, from, to)))
  }

  override suspend fun dropSlot(context: SettingsContext, mapKey: SettingsMapKey, value: String) {
    applyMutations(listOf(DropSlotMutation(context, mapKey, value)))
  }

  // Copy then drop in one batch: both apply under a single lock hold with nothing interleaved, so the rename is atomic (ADR 0132).
  override suspend fun renameSlot(context: SettingsContext, mapKey: SettingsMapKey, from: String, to: String) {
    applyMutations(listOf(CopySlotMutation(context, mapKey, from, to), DropSlotMutation(context, mapKey, from)))
  }

  /**
   * Applies [requested] atomically under a single lock hold, in order. Each entry applies to the batch model before
   * the next one, and the batch model swaps in once. First every mutation is validated and its key confirmed, so a
   * rejected entry fails the whole batch before anything applies. Then a mutation at a context of a project that is
   * not open is dropped with a warning ([isAtNotOpenProject]).
   *
   * The two kinds of entry apply differently. A [SetMutation] is routed to one layer, which [planMutation] resolves up
   * front. A [SlotMutation] is broadcast to every layer at its context that holds the slot, with no single target
   * (see [broadcastSlotMutation]). Last, each affected storage unit gets a scheduled save.
   */
  private suspend fun applyMutations(requested: List<Mutation>) {
    requested.forEach { it.validate() }
    confirmKeys(requested)
    val mutations = requested.filter { mutation ->
      val dropped = isAtNotOpenProject(mutation.context)
      if (dropped) {
        val identity = mutation.context.realContext.identity
        LOG.warn("Dropping a mutation of '${mutation.key.identity}' at '$identity': its project is not open")
      }
      !dropped
    }
    if (mutations.isEmpty()) return
    // Slot ops broadcast, so only the set entries need a target.
    val setPlans = HashMap<Mutation, MutationPlan>()
    for (mutation in mutations) {
      if (mutation is SetMutation<*>) setPlans[mutation] = planMutation(mutation)
    }
    // Outside the lock on purpose: this waits for the covering reload to finish, and the reload finishes by
    // installing under vaultWriteMutex — waiting while holding it would deadlock. Once the writes apply below,
    // pendingSaves guards them from a reload that starts afterward.
    awaitAllReady(mutations.map { it.context })
    val settingsChangeEvents = SettingsChangeEvents()
    val storageUnitsToSave = LinkedHashSet<Pair<SettingsContext, LayerCoordinate>>()
    vaultWriteMutex.withLock {
      var batchModel = model
      for (mutation in mutations) batchModel = when (mutation) {
        is SetMutation<*> -> applySet(batchModel, setPlans.getValue(mutation), settingsChangeEvents, storageUnitsToSave)
        is SlotMutation -> broadcastSlotMutation(batchModel, mutation, settingsChangeEvents, storageUnitsToSave)
      }
      // One swap for the whole batch, so a lock-free reader sees the model before the batch or after it (ADR 0136).
      model = batchModel
      // Mark before releasing the lock so no spontaneous push can clobber a mutation before its write lands.
      storageUnitsToSave.forEach { (storageContext, coordinate) -> pendingSaves.add(storageContext.identity to coordinate) }
    }
    notifyListeners(settingsChangeEvents)
    storageUnitsToSave.forEach { (storage, coordinate) -> scheduleSave(storage, coordinate) }
  }

  /**
   * Returns whether [context] belongs to a project that is not open, such as a closed project or the default project.
   * The vault has no model for such a project, so [applyMutations] drops a mutation there instead of letting it fall
   * through to the application context (ADR 0155). A read at such a context still resolves through the application
   * layers.
   */
  private fun isAtNotOpenProject(context: SettingsContext): Boolean {
    val projectAware = context.realContext as? ProjectAware ?: return false
    return projectAware.project == null
  }

  /**
   * Confirms every key of [mutations] against the key registry, which waits for the key discovery to finish. It does
   * nothing when [registryChecksEnabled] is `false`.
   *
   * @throws IllegalStateException when a key cannot register, because another key holds its identity
   */
  private suspend fun confirmKeys(mutations: List<Mutation>) {
    if (!registryChecksEnabled) return
    val registry = SettingsKeyRegistry.getInstance()
    for (mutation in mutations) {
      check(registry.confirmForWrite(mutation.key)) {
        "Settings key '${mutation.key.identity}' cannot be written: another key holds that identity."
      }
    }
  }

  /**
   * Applies a routed [SetMutation] plan into its single resolved `(context, locality, provider)` layer of [batchModel].
   * Call under [vaultWriteMutex].
   *
   * @return [batchModel] with the entry applied; the caller publishes it once for the whole batch
   */
  private fun applySet(
    batchModel: Map<String, ContextLayers>,
    plan: MutationPlan,
    settingsChangeEvents: SettingsChangeEvents,
    storageUnitsToSave: MutableSet<Pair<SettingsContext, LayerCoordinate>>,
  ): Map<String, ContextLayers> {
    // apply() needs the resolved (possibly access-pinned) context for the discriminator; the model stores the real
    // context, since its identity is what layers are keyed by and an overlay must not leak into the cache.
    val realContext = plan.targetContext.realContext
    val coordinate = LayerCoordinate(plan.provider, plan.locality)
    val targetLayer = batchModel[realContext.identity]?.layers?.get(coordinate) ?: SettingsValue.EMPTY_MAP
    val mutatedLayer = plan.entry.apply(targetLayer, plan.targetContext)
    collectChangeEvents(realContext, targetLayer, mutatedLayer, settingsChangeEvents)
    storageUnitsToSave.add(plan.provider.storageContext(plan.targetContext) to coordinate)
    return batchModel.install(realContext, coordinate, mutatedLayer)
  }

  /**
   * Broadcasts a [SlotMutation] to every (provider, locality) layer at its context that holds the slot (ADR 0078). It
   * is an edit of the whole scope, not a routed write. So a copy, a drop or a rename reaches every contributor: each
   * locality and each provider, not the one layer that a plain write resolves to. It matches the context of the
   * operation directly and does not walk up the chain (ADR 0079). [SlotMutation.apply] changes nothing where the slot
   * is absent, so only the layers that hold the slot change.
   *
   * It skips a layer that is no [candidate][isCandidate] for the map key, or whose locality takes no write at the
   * context ([LayerProvider.availableLocalities]). So it skips a read-only layer too. It ignores the
   * [locality][SettingsKey.locality] of the map key on purpose: a map key is a storage dimension, not a localized
   * value (ADR 0077). Call under [vaultWriteMutex].
   *
   * @return [batchModel] with the slot operation applied; the caller publishes it once for the whole batch
   */
  private fun broadcastSlotMutation(
    batchModel: Map<String, ContextLayers>,
    mutation: SlotMutation,
    settingsChangeEvents: SettingsChangeEvents,
    storageUnitsToSave: MutableSet<Pair<SettingsContext, LayerCoordinate>>,
  ): Map<String, ContextLayers> {
    val realContext = mutation.context.realContext
    val layers = batchModel[realContext.identity]?.layers ?: return batchModel
    var result = batchModel
    for ((coordinate, layer) in layers) {
      if (!takesWrite(coordinate.provider, coordinate.locality, realContext, mutation.mapKey)) continue
      val mutatedLayer = mutation.apply(layer, realContext)
      if (mutatedLayer == layer) continue
      collectChangeEvents(realContext, layer, mutatedLayer, settingsChangeEvents)
      result = result.install(realContext, coordinate, mutatedLayer)
      storageUnitsToSave.add(coordinate.provider.storageContext(realContext) to coordinate)
    }
    return result
  }

  override fun mutation(context: SettingsContext): SettingsMutationBuilder = SettingsMutationBuilderImpl(context)

  /** Collects [Mutation] entries fluently and applies them as one atomic batch on [apply]. See [SettingsMutationBuilder]. */
  private inner class SettingsMutationBuilderImpl(private var currentContext: SettingsContext) : SettingsMutationBuilder {
    private val mutations = mutableListOf<Mutation>()

    override fun context(context: SettingsContext): SettingsMutationBuilder = apply { currentContext = context }

    override fun <T> set(key: SettingsKey<T>, value: T, context: SettingsContext?): SettingsMutationBuilder =
      apply { mutations.add(SetMutation(context ?: currentContext, key, value)) }

    override fun copySlot(mapKey: SettingsMapKey, from: String, to: String, context: SettingsContext?): SettingsMutationBuilder =
      apply { mutations.add(CopySlotMutation(context ?: currentContext, mapKey, from, to)) }

    override fun dropSlot(mapKey: SettingsMapKey, value: String, context: SettingsContext?): SettingsMutationBuilder =
      apply { mutations.add(DropSlotMutation(context ?: currentContext, mapKey, value)) }

    override fun renameSlot(mapKey: SettingsMapKey, from: String, to: String, context: SettingsContext?): SettingsMutationBuilder =
      apply {
        val at = context ?: currentContext
        mutations.add(CopySlotMutation(at, mapKey, from, to))
        mutations.add(DropSlotMutation(at, mapKey, from))
      }

    override suspend fun apply(): Unit = applyMutations(mutations)
  }

  /**
   * Plans [mutation] for the batch. It resolves the target: the first `(context, locality, provider)` that
   * [takes the write][takesWrite] of the key. The context is the outer axis. The walk goes up from the context of the
   * mutation and skips each context that the [contextFilter][SettingsKey.contextFilter] of the key rejects. So the
   * write lands at the narrowest accepting context. This agrees with the read precedence, where the context outranks
   * the locality (ADR 0073).
   *
   * Within a context, the localities come in the order of [writeLocalities]. Within a locality, the providers come
   * narrowest first, the reverse of the EP order. The engine matches the locality, not the provider: a provider only
   * [declares which localities take a write][LayerProvider.availableLocalities].
   *
   * @throws IllegalStateException if no layer in the chain takes the write
   */
  private fun planMutation(mutation: Mutation): MutationPlan {
    val localities = writeLocalities(mutation.key, mutation.context)
    for (ctx in generateSequence(mutation.context) { it.parentContext }) {
      if (!mutation.key.contextFilter(ctx)) continue
      for (locality in localities) {
        for (provider in LayerProvider.EP_NAME.extensionList.asReversed()) {
          if (takesWrite(provider, locality, ctx, mutation.key)) return MutationPlan(mutation, ctx, provider, locality)
        }
      }
    }
    error("No layer took the write of '${mutation.key.name}' at context '${mutation.context.identity}' or any parent.")
  }

  /**
   * Returns whether the [locality] layer of [provider] at [context] takes a write of [key]. The provider must be a
   * [candidate][isCandidate] for [key] and must [offer][LayerProvider.availableLocalities] [locality] at [context].
   * That is the only write check on the context (ADR 0129).
   */
  private fun takesWrite(provider: LayerProvider, locality: LayerLocality, context: SettingsContext, key: SettingsKey<*>): Boolean =
    isCandidate(provider, key) && locality in provider.availableLocalities(context)

  /**
   * Returns the localities that a write of [key] at [context] tries, in order. Normally this is the
   * [KeyLocality.writePreference] of the key. When [EDIT_TEAM_LAYER] resolves `true` at [context], a shareable write
   * tries `TEAM` first. This is the team-editing mode (ADR 0076). The mode itself is environmental, so the read of it
   * here never recurses.
   */
  private fun writeLocalities(key: SettingsKey<*>, context: SettingsContext): List<LayerLocality> =
    if (key.locality == KeyLocality.SHAREABLE && EDIT_TEAM_LAYER[context]) listOf(LayerLocality.TEAM, LayerLocality.LOCAL)
    else key.locality.writePreference

  // -- persistence -----------------------------------------------------------------------------------------------

  private fun scheduleSave(storageContext: SettingsContext, coordinate: LayerCoordinate) {
    savingChannels.computeIfAbsent(storageContext.identity to coordinate) { saverChannel(storageContext, coordinate) }.trySend(Unit)
  }

  private fun saverChannel(storageContext: SettingsContext, coordinate: LayerCoordinate): Channel<Unit> {
    val channel = Channel<Unit>(Channel.CONFLATED)
    scope.launch {
      while (channel.receiveCatching().isSuccess) {
        delay(PERSIST_DEBOUNCE)
        persistNow(storageContext, coordinate)
      }
    }
    return channel
  }

  /**
   * Writes the current model slice of one storage unit to the disk at once and clears its pending-save marker. The
   * debounced saver and a forced [flush] share this body. A per-unit lock of [writeMutexes] keeps the two from writing
   * the same file at once (see [writeMutexes]).
   *
   * The marker clears only when the disk matches the model. A mutation during the write leaves the model ahead. Then
   * the marker stays, and the buffered save signal of that mutation persists the unit again. A dropped save removes
   * every slice of the store from the model, also a slice that a write changed during the save, because the store has
   * no place for any of them (ADR 0150). On a failure the marker clears, so the pushes of this store are not blocked
   * forever. A later mutation marks the unit again and retries.
   */
  private suspend fun persistNow(storageContext: SettingsContext, coordinate: LayerCoordinate) {
    val unit = storageContext.identity to coordinate
    writeMutexes.computeIfAbsent(unit) { Mutex() }.withLock {
      val storageContextData = vaultWriteMutex.withLock {
        if (unit !in pendingSaves) return  // already persisted by the saver or a concurrent flush
        storageContextData(storageContext, coordinate)
      }
      try {
        val saveResult = coordinate.provider.writeData(storageContext, coordinate.locality, storageContextData)
        val settingsChangeEvents = SettingsChangeEvents()
        vaultWriteMutex.withLock {
          if (saveResult == SaveResult.DROPPED) {
            for ((context, layer) in storageContextData(storageContext, coordinate)) {
              collectChangeEvents(context, layer, null, settingsChangeEvents)
              model = model.without(context, coordinate)
            }
            pendingSaves.remove(unit)
          }
          else if (storageContextData(storageContext, coordinate) == storageContextData) {
            pendingSaves.remove(unit)
          }
        }
        if (saveResult == SaveResult.DROPPED) notifyListeners(settingsChangeEvents)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.error("Failed to persist settings for '${storageContext.identity}'", e)
        vaultWriteMutex.withLock { pendingSaves.remove(unit) }
      }
    }
  }

  override suspend fun flush(scopeContext: SettingsContext) {
    val units = vaultWriteMutex.withLock { pendingUnitsUnder(scopeContext) }
    // Each unit is a separate store with its own write mutex, so persist them concurrently. A flush runs at the
    // shutdown and at a project close, where the wall-clock time matters.
    coroutineScope { units.forEach { (storageContext, coordinate) -> launch { persistNow(storageContext, coordinate) } } }
  }

  /**
   * Collects the storage units that [flush] must drain. It resolves the [storage context][LayerProvider.storageContext]
   * of each layer in the model under [scopeContext], and keeps the units in [pendingSaves]. Many contexts can share one
   * unit. Call under [vaultWriteMutex].
   *
   * @return the distinct storage units, each as its storage context and coordinate, that have a pending save
   */
  private fun pendingUnitsUnder(scopeContext: SettingsContext): List<Pair<SettingsContext, LayerCoordinate>> {
    val units = LinkedHashMap<Pair<String, LayerCoordinate>, Pair<SettingsContext, LayerCoordinate>>()
    for (entry in model.values) {
      if (!scopeContext.isAncestorOf(entry.context, strict = false)) continue
      for (coordinate in entry.layers.keys) {
        val storageContext = coordinate.provider.storageContext(entry.context)
        val unit = storageContext.identity to coordinate
        if (unit in pendingSaves) units.putIfAbsent(unit, storageContext to coordinate)
      }
    }
    return units.values.toList()
  }

  /**
   * Returns every model slice that persists into [storageContext] under [coordinate], as `context -> its layer map`.
   * Call under [vaultWriteMutex].
   */
  private fun storageContextData(storageContext: SettingsContext, coordinate: LayerCoordinate): Map<SettingsContext, MapValue> =
    model.values.mapNotNull { entry ->
      val layer = entry.layers[coordinate] ?: return@mapNotNull null
      if (coordinate.provider.storageContext(entry.context).identity == storageContext.identity) entry.context to layer else null
    }.toMap()

  // -- snapshot cache --------------------------------------------------------------------------------------------

  /**
   * Dumps the layers that belong in the snapshot cache of [project], or in the application cache when [project] is
   * `null`. A context that names a project (a [ProjectAware]) belongs to the cache of that project. Every other context
   * belongs to the application cache. Each layer carries its context identity, the class name of its provider and its
   * locality, so [installCached] can rebuild the address on a load. It reads the volatile model with no lock.
   */
  internal fun dump(project: Project?): List<CachedLayer> {
    val result = ArrayList<CachedLayer>()
    for (entry in model.values) {
      val inScope = if (project == null) entry.context !is ProjectAware else (entry.context as? ProjectAware)?.project == project
      if (!inScope) continue
      for ((coordinate, value) in entry.layers) {
        result.add(CachedLayer(entry.context.identity, coordinate.provider.javaClass.name, coordinate.locality.name, value))
      }
    }
    // A canonical order, not the insertion order of the model, makes the dump a pure function of the content.
    // SettingsSnapshotCache skips an unchanged dump by a compare of whole dumps, and a reorder must not cause a write.
    return result.sortedWith(compareBy({ it.contextId }, { it.providerId }, { it.locality }))
  }

  /**
   * Seeds the model from cached [layers] on a snapshot-cache load. [ContextFactory.deserialize] rebuilds the context
   * of each layer from its stored identity, so the vault knows no context type. The provider resolves by its class
   * name against the live extension list. The vault skips a layer whose context, provider or locality does not
   * resolve now. It keeps a layer that is already in the model, so a reload that came before the cache load is never
   * overwritten. Nothing is marked pending, because the cache is not a mutation. The boot reload reconciles the seeded
   * model.
   */
  internal suspend fun installCached(layers: List<CachedLayer>) {
    if (layers.isEmpty()) return
    val providersByName = LayerProvider.EP_NAME.extensionList.associateBy { it.javaClass.name }
    val contextFactory = ContextFactory.getInstance()
    val resolved = layers.mapNotNull { cached ->
      val provider = providersByName[cached.providerId] ?: return@mapNotNull null
      val locality = LayerLocality.entries.firstOrNull { it.name == cached.locality } ?: return@mapNotNull null
      val context = contextFactory.deserialize(cached.contextId) ?: return@mapNotNull null
      Triple(context, LayerCoordinate(provider, locality), cached.value)
    }
    if (resolved.isEmpty()) return
    val settingsChangeEvents = SettingsChangeEvents()
    vaultWriteMutex.withLock {
      var currentModel = model
      for ((context, coordinate, value) in resolved) {
        if (currentModel[context.identity]?.layers?.containsKey(coordinate) == true) continue
        collectChangeEvents(context, null, value, settingsChangeEvents)
        currentModel = currentModel.install(context, coordinate, value)
      }
      model = currentModel
    }
    notifyListeners(settingsChangeEvents)
  }

  // -- push folding ----------------------------------------------------------------------------------------------

  /**
   * Folds [firstLayerData] and every push already queued behind it into the model under one lock hold. It records the
   * watches that each push touches, then delivers them once. The drain to an empty queue coalesces a burst, such as a
   * branch switch that lands as many pushes. A contextual watch fires at most once, and not at all when its value flips
   * back within the burst. A key-only watch fires at most once per changed context. A push whose store is in a dirty
   * context goes to [heldPushes] instead (ADR 0123, ADR 0160).
   */
  private suspend fun processPendingPushes(firstLayerData: LayerData) {
    val settingsChangeEvents = SettingsChangeEvents()
    vaultWriteMutex.withLock {
      var currentModel = model
      var layerData: LayerData? = firstLayerData
      while (layerData != null) {
        if (isStoreDirty(layerData)) heldPushes.add(layerData)
        else currentModel = processPush(currentModel, layerData, settingsChangeEvents)
        layerData = incoming.tryReceive().getOrNull()
      }
      model = currentModel
    }
    notifyListeners(settingsChangeEvents)
  }

  /**
   * Folds one push into [currentModel]. It drops the push while a save is pending for the store. The file that the
   * push read is stale, because the applied mutation is not persisted yet, and an install would overwrite the mutation.
   * Otherwise a push that matches the model changes nothing, which absorbs the echo of an own write. A push that
   * differs is an external change.
   *
   * @return [currentModel] with the push applied
   */
  private fun processPush(
    currentModel: Map<String, ContextLayers>,
    layerData: LayerData,
    settingsChangeEvents: SettingsChangeEvents,
  ): Map<String, ContextLayers> {
    val coordinate = LayerCoordinate(layerData.provider, layerData.locality)
    if ((layerData.provider.storageContext(layerData.context).identity to coordinate) in pendingSaves) return currentModel
    val currentData = currentModel[layerData.context.identity]?.layers?.get(coordinate)
    val newData = if (layerData.isEmpty) null else layerData.map
    if (currentData == newData) return currentModel
    collectChangeEvents(layerData.context, currentData, newData, settingsChangeEvents)
    return if (newData == null) currentModel.without(layerData.context, coordinate)
    else currentModel.install(layerData.context, coordinate, newData)
  }

  // -- change watchers -------------------------------------------------------------------------------------------

  override fun addListener(key: SettingsKey<*>, disposable: Disposable, listener: SettingsChangeListener) {
    register(SettingsKeyWatch(key, context = null, listener), disposable)
  }

  override fun addListener(key: SettingsKey<*>, context: SettingsContext, disposable: Disposable, listener: SettingsChangeListener) {
    register(SettingsKeyWatch(key, context, listener), disposable)
  }

  private fun register(settingsKeyWatch: SettingsKeyWatch, disposable: Disposable) {
    require(settingsKeyWatch.key !is SettingsMapKey) {
      "Cannot listen on a SettingsMapKey ('${settingsKeyWatch.key.name}'): it holds slotted storage, not a value — listen on its child keys."
    }
    mySettingsKeyWatches.add(settingsKeyWatch)
    Disposer.register(disposable) { unregister(settingsKeyWatch) }
  }

  /**
   * Removes [settingsKeyWatch] from [mySettingsKeyWatches] and from [queuedNotifications] in one [notificationLock]
   * hold. So a disposed watch does not stay in the queue, and it does not fire from the queue.
   */
  private fun unregister(settingsKeyWatch: SettingsKeyWatch) {
    synchronized(notificationLock) {
      mySettingsKeyWatches.remove(settingsKeyWatch)
      queuedNotifications.contextual.remove(settingsKeyWatch)
      queuedNotifications.keyOnly.removeIf { it.settingsKeyWatch === settingsKeyWatch }
    }
  }

  /**
   * Records into [settingsChangeEvents] the watches whose key the change at [changeContext] from [currentData] to
   * [newData] touches. Here a contextual watch is only a candidate. [takeReadyNotifications] resolves its value again
   * when its context is ready, and fires it only on a net change, which is the real gate. So this method does not
   * check whether [changeContext] is in the resolution chain of the watch. The new resolve covers every way a change
   * can alter the value, or not alter it. This includes a cancellation across layers, such as a project write that
   * restores the value of a file after an application write changed it.
   */
  private fun collectChangeEvents(
    changeContext: SettingsContext,
    currentData: MapValue?,
    newData: MapValue?,
    settingsChangeEvents: SettingsChangeEvents,
  ) {
    if (mySettingsKeyWatches.isEmpty()) return
    val changedKeyIdentities = HashSet<String>().also { collectChanges(currentData, newData, it) }
    if (changedKeyIdentities.isEmpty()) return
    for (watch in mySettingsKeyWatches) {
      if (watch.dependencyKeys.none(changedKeyIdentities::contains)) continue
      if (watch.context == null) {
        settingsChangeEvents.keyOnly.add(SettingsKeyChangeEvent(watch, changeContext))
      }
      else {
        settingsChangeEvents.contextual.add(watch)
      }
    }
  }

  /**
   * Queues [events], then delivers every queued notification whose context is ready (ADR 0161). A notification at a
   * dirty context stays in [queuedNotifications]. Call it after every model swap and after every readiness change, out
   * of [vaultWriteMutex]. The listeners run with no lock held. A disposed watch does not fire.
   */
  private fun notifyListeners(events: SettingsChangeEvents) {
    // Every model swap funnels through here; ping the snapshot cache so it re-dumps and (if changed) re-saves.
    modelChanged.tryEmit(Unit)
    for ((watch, context) in takeReadyNotifications(events)) {
      if (watch in mySettingsKeyWatches) notifyListeners(watch, context)
    }
  }

  /**
   * Adds [events] of the registered watches to [queuedNotifications], and takes out every queued notification whose
   * context is ready, in one [notificationLock] hold. A contextual watch resolves its value and is due only on a net
   * change. Then it takes the new value as its [current value][SettingsKeyWatch.currentValue]. So it fires at most
   * once, also when several changes affected it. A key-only event is due once per pair of a watch and a changed
   * context.
   *
   * @return each due watch with the context that its listener gets, the contextual watches first
   */
  private fun takeReadyNotifications(events: SettingsChangeEvents): List<Pair<SettingsKeyWatch, SettingsContext>> =
    synchronized(notificationLock) {
      events.contextual.filterTo(queuedNotifications.contextual) { it in mySettingsKeyWatches }
      events.keyOnly.filterTo(queuedNotifications.keyOnly) { it.settingsKeyWatch in mySettingsKeyWatches }
      val dueNotifications = ArrayList<Pair<SettingsKeyWatch, SettingsContext>>()
      queuedNotifications.contextual.removeIf { watch ->
        val context = watch.context!!
        if (isDirty(context)) return@removeIf false
        val value = watch.key.resolveRaw(context)
        if (value != watch.currentValue) {
          watch.currentValue = value
          dueNotifications.add(watch to context)
        }
        true
      }
      queuedNotifications.keyOnly.removeIf { event ->
        if (isDirty(event.changeContext)) return@removeIf false
        dueNotifications.add(event.settingsKeyWatch to event.changeContext)
        true
      }
      dueNotifications
    }

  private fun notifyListeners(settingsKeyWatch: SettingsKeyWatch, context: SettingsContext) {
    try {
      settingsKeyWatch.listener.onChanged(settingsKeyWatch.key, context)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.error("Settings change listener for '${settingsKeyWatch.key.name}' failed", e)
    }
  }

  /**
   * Adds every changed key identity to [changedKeys], and descends only into a slotted storage. A key marked with
   * [STORAGE_IDENTITY_PREFIX][SettingsKey.STORAGE_IDENTITY_PREFIX] holds a map of slots. So the method recurses through
   * its slot values to reach the child identities, and skips the slot keys, which are arbitrary discriminator values.
   * Any other key is a value key. Its payload (a scalar, a list or a serialized object map) is opaque, and the method
   * never enters it. So a single changed leaf gives only the identity of that leaf, and no object field leaks into the
   * result.
   */
  private fun collectChanges(currentData: SettingsValue?, newData: SettingsValue?, changedKeys: MutableSet<String>) {
    val currentValues = (currentData as? MapValue)?.values ?: emptyMap()
    val newValues = (newData as? MapValue)?.values ?: emptyMap()
    for (key in currentValues.keys + newValues.keys) {
      val currentValue = currentValues[key]
      val newValue = newValues[key]
      if (currentValue == newValue) continue
      if (key.startsWith(SettingsKey.STORAGE_IDENTITY_PREFIX) && (currentValue is MapValue || newValue is MapValue)) {
        val currentSlots = (currentValue as? MapValue)?.values ?: emptyMap()
        val newSlots = (newValue as? MapValue)?.values ?: emptyMap()
        for (slot in currentSlots.keys + newSlots.keys) {
          collectChanges(currentSlots[slot], newSlots[slot], changedKeys)
        }
      }
      else {
        changedKeys.add(key)
      }
    }
  }

  // -- model snapshot helpers (copy-on-write) --------------------------------------------------------------------

  /**
   * Addresses a layer within one context: its [locality] (team, local or environmental) and the [provider] that owns
   * it. The locality is a first-class axis, because the resolution orders by context, then locality, then provider
   * (ADR 0073). It is a separate field, so the model can grow more axes later and not fold them into the provider.
   */
  private data class LayerCoordinate(val provider: LayerProvider, val locality: LayerLocality)

  /**
   * Holds the layers of one context, keyed by [LayerCoordinate]. It is immutable, and a writer swaps it as a whole under
   * [vaultWriteMutex].
   */
  private class ContextLayers(val context: SettingsContext, val layers: Map<LayerCoordinate, MapValue>) {
    /** Returns a copy without the layers of [provider] in every locality. [forgetProvider] uses it when the provider unloads. */
    fun without(provider: LayerProvider): ContextLayers = ContextLayers(context, layers.filterKeys { it.provider != provider })
  }

  private fun Map<String, ContextLayers>.install(
    context: SettingsContext,
    coordinate: LayerCoordinate,
    newData: MapValue,
  ): Map<String, ContextLayers> {
    val existing = this[context.identity]
    val merged = ContextLayers(existing?.context ?: context, (existing?.layers ?: emptyMap()) + (coordinate to newData))
    return this + (context.identity to merged)
  }

  private fun Map<String, ContextLayers>.without(context: SettingsContext, coordinate: LayerCoordinate): Map<String, ContextLayers> {
    val existing = this[context.identity] ?: return this
    val remaining = existing.layers - coordinate
    return if (remaining.isEmpty()) this - context.identity else this + (context.identity to ContextLayers(existing.context, remaining))
  }

  // -- mutation batch entries ------------------------------------------------------------------------------------

  /**
   * Represents one declared entry of a batch. A [SetMutation] carries two different contexts:
   * - [context] is where the caller requested the write, the origin of the resolution. [planMutation] walks up from
   *   here to find the target layer, so the write does not always land here. It can be an
   *   [access][com.intellij.platform.settings.v2.access] overlay that pins discriminator values.
   * - the `targetContext` given to [apply] is where the write lands, as [planMutation] chooses it. It is the first
   *   context of the chain with an accepting layer. It equals [context] unless the write went up the chain.
   *
   * A [SlotMutation] uses only [context]. It matches the context directly, with no walk up, and broadcasts across the
   * layers of that context.
   */
  private sealed class Mutation {
    /**
     * Holds the context that the caller targeted. For a set, [planMutation] resolves from it. A slot operation matches
     * it directly.
     */
    abstract val context: SettingsContext

    /** Holds the key of this mutation: a value key for a set, a [SettingsMapKey] for a slot operation. */
    abstract val key: SettingsKey<*>

    /**
     * Checks the mutation before anything in the batch applies, such as a read-only key or an invalid value. It is
     * fast and has no side effects. It throws when it rejects the mutation.
     */
    open fun validate() {}

    /**
     * Returns the mutated copy of the layer [map].
     *
     * @param targetContext the resolved landing context of a set. A slot operation does not use it, because its
     *   transform does not depend on a context.
     */
    abstract fun apply(map: MapValue, targetContext: SettingsContext): MapValue
  }

  private class SetMutation<T>(override val context: SettingsContext, override val key: SettingsKey<T>, private val value: T) : Mutation() {
    override fun validate() {
      require(key !is SettingsMapKey) {
        "A SettingsMapKey ('${key.name}') cannot be written directly; write its child keys, which persist in its slots."
      }
      if (key.readonly) throw ReadOnlySettingsException(key)
      key.validate(value)?.let { throw SettingsValidationException(key, value, it) }
      key.requirePinnedSlots(context)
    }

    override fun apply(map: MapValue, targetContext: SettingsContext): MapValue = map.with(targetContext, key, value)
  }

  /**
   * Represents a slot operation. The vault does not route it to one layer, as it does a [SetMutation]. The vault
   * broadcasts it to every layer at its [context] that holds the slot (see [broadcastSlotMutation]). [apply] is the
   * context-free transform of the sub-maps of the map key in one layer.
   */
  private sealed class SlotMutation : Mutation() {
    abstract val mapKey: SettingsMapKey
    override val key: SettingsKey<*> get() = mapKey
  }

  private class CopySlotMutation(
    override val context: SettingsContext,
    override val mapKey: SettingsMapKey,
    private val from: String,
    private val to: String,
  ) : SlotMutation() {
    override fun apply(map: MapValue, targetContext: SettingsContext): MapValue = map.copySubMap(mapKey.identity, from, to)
  }

  private class DropSlotMutation(override val context: SettingsContext, override val mapKey: SettingsMapKey, private val value: String) :
    SlotMutation() {
    override fun apply(map: MapValue, targetContext: SettingsContext): MapValue = map.dropSubMap(mapKey.identity, value)
  }

  private class MutationPlan(val entry: Mutation, val targetContext: SettingsContext, val provider: LayerProvider, val locality: LayerLocality)

  // -- reload bookkeeping ----------------------------------------------------------------------------------------

  /**
   * Holds the readiness state of one requested reload of [scopeContext], which runs or waits in the queue
   * (ADR 0160). See [unfinishedReloads].
   */
  private class UnfinishedReload(val scopeContext: SettingsContext) {
    /**
     * Holds the kinds whose bucket this reload has not installed yet. A queued reload has every kind pending. The
     * vault changes it under [vaultWriteMutex], and [isDirty] reads it with no lock.
     */
    @Volatile
    var pendingKinds: Set<SettingsContextKind> = SettingsContextKind.entries.toSet()

    /**
     * Returns whether this reload keeps [context] dirty. It does when [scopeContext] is [context] or an ancestor of
     * it, and the kind of [context] or a wider kind is in [pendingKinds].
     *
     * @param pendingKinds the pending kinds to check against, the current ones by default
     */
    fun keepsDirty(context: SettingsContext, pendingKinds: Set<SettingsContextKind> = this.pendingKinds): Boolean =
      scopeContext.isAncestorOf(context, strict = false) && pendingKinds.any { it <= context.kind }
  }

  /**
   * Holds the reload mutexes that one reload holds (ADR 0162). A mutex is released once, either when the reload is
   * done with its kind or at the end of the reload. So a failed or a cancelled reload leaks no mutex.
   */
  private class HeldReloadMutexes(private val reloadMutexes: Map<SettingsContextKind, Mutex>) {
    private val heldKinds = ConcurrentHashMap.newKeySet<SettingsContextKind>()

    /** Takes the reload mutex of [kind], and suspends until it gets it. */
    suspend fun lock(kind: SettingsContextKind) {
      reloadMutexes.getValue(kind).lock()
      heldKinds.add(kind)
    }

    /** Releases the reload mutex of [kind]. It does nothing when the reload has released it already. */
    fun unlock(kind: SettingsContextKind) {
      if (heldKinds.remove(kind)) reloadMutexes.getValue(kind).unlock()
    }

    /** Releases every reload mutex that is still held. Call it only when no other coroutine of the reload runs. */
    fun unlockAll() {
      for (kind in heldKinds.toList()) unlock(kind)
    }
  }

  // -- watch bookkeeping -----------------------------------------------------------------------------------------

  /**
   * Represents one registered change watch. A `null` [context] marks a key-only watch, which has no fixed context and
   * no value deduplication.
   */
  private class SettingsKeyWatch(val key: SettingsKey<*>, val context: SettingsContext?, val listener: SettingsChangeListener) {
    /** Holds the identities that a changed layer must touch for this watch to react. See [dependencyRoots]. */
    val dependencyKeys: Set<String> = key.dependencyRoots()

    /**
     * Holds the last raw resolved value, for the change deduplication. The values compare undecoded. A key-only watch
     * does not use it. After the construction, it is guarded by [notificationLock].
     */
    var currentValue: SettingsValue? = context?.let { key.resolveRaw(it) }
  }

  /**
   * Pairs a key-only watch with the context where the vault detected a change. A burst delivers each pair once, and
   * [queuedNotifications] holds each pair once.
   */
  private data class SettingsKeyChangeEvent(val settingsKeyWatch: SettingsKeyWatch, val changeContext: SettingsContext)

  /**
   * Collects the contextual watches and the key-only events, with no duplicates and in arrival order. One instance
   * collects the notifications of one model swap, and [notifyListeners] delivers them. [queuedNotifications] is the
   * instance that holds the notifications at a dirty context.
   */
  private class SettingsChangeEvents {
    val contextual = LinkedHashSet<SettingsKeyWatch>()
    val keyOnly = LinkedHashSet<SettingsKeyChangeEvent>()
  }

  companion object {
    private val LOG = logger<SettingsVaultImpl>()

    /**
     * Holds how long a store stays quiet before the vault persists its coalesced changes. It is small: it folds a burst,
     * but does not delay a click.
     */
    private val PERSIST_DEBOUNCE: Duration = 100.milliseconds

    /**
     * Returns the identities that a change to this key depends on: its own identity and the identity of every
     * discriminator up its storage chain.
     */
    private fun SettingsKey<*>.dependencyRoots(): Set<String> =
      (sequenceOf(identity) + storageChain().map { it.discriminatorKey.identity }).toCollection(LinkedHashSet())
  }
}
