// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.with
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the layers of a test in memory. By default it offers every locality at every context, so a `key[ctx] = v`
 * through the vault lands in this layer. A slotted child key nests into the slot of its map key, as in a production
 * layer. [keyFilter] limits the keys that it holds.
 *
 * The [seed] and [put] helpers **pre-populate** a layer of this provider, as if the provider had loaded it. A test
 * that stacks several providers needs them to control which layer holds what. A write through the vault cannot do
 * that, because it goes to the first accepting layer. A helper **pushes** a typed key, a whole [MapValue], or a raw
 * value under an identity. A push applies **asynchronously**, so a test polls with [awaitEquals]. [writeData] only
 * records the [saved] slices, so the vault model stays the truth.
 *
 * @param id the name that a [SettingsKey.layerFilter] can match
 * @param localitiesAt the [LayerProvider.availableLocalities] answer of this provider at a context
 * @param keyFilter the [LayerProvider.acceptsKey] answer of this provider
 */
internal class TestLayerProvider(
  val id: String = "",
  private val localitiesAt: (SettingsContext) -> Set<LayerLocality> = { LayerLocality.entries.toSet() },
  private val keyFilter: (SettingsKey<*>) -> Boolean = { true },
) : LayerProvider {
  private val store = ConcurrentHashMap<Pair<SettingsContext, LayerLocality>, MapValue>()
  private val heldReads = ConcurrentHashMap<String, HeldRead>()
  private val savedSlices = ConcurrentHashMap<Pair<String, LayerLocality>, MapValue>()
  private val heldSave = AtomicReference<HeldSave?>()

  /**
   * Runs on every [storageContext] call. The vault calls [storageContext] while it applies a batch, so a test can read
   * settings there to see what a reader on another thread would see in the middle of the batch.
   */
  @Volatile
  var onStorageContext: (() -> Unit)? = null

  /** Counts every [writeData] call, also a held or a dropped one. */
  val saveAttempts: AtomicInteger = AtomicInteger()

  override fun acceptsKey(key: SettingsKey<*>): Boolean = keyFilter(key)

  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> = localitiesAt(context)

  override fun storageContext(context: SettingsContext): SettingsContext {
    onStorageContext?.invoke()
    return context.realContext
  }

  override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>): SaveResult {
    saveAttempts.incrementAndGet()
    heldSave.getAndSet(null)?.let { held ->
      held.started.complete(Unit)
      held.release.await()
      if (held.result == SaveResult.DROPPED) return SaveResult.DROPPED
    }
    for ((context, slice) in slices) savedSlices[context.identity to locality] = slice
    return SaveResult.SAVED
  }

  /**
   * Holds the next [writeData] after it starts, and makes it return [result] once released. A write or a push
   * meanwhile meets a save that is still pending.
   *
   * @param result what the held save returns; a [SaveResult.SAVED] save also records its slices
   * @return the handle that reports the start of the save and releases it
   */
  fun holdNextSave(result: SaveResult = SaveResult.DROPPED): HeldSave = HeldSave(result).also { heldSave.set(it) }

  /**
   * Reports that a held [writeData] started, and releases it. See [holdNextSave].
   *
   * @param result what the held save returns
   */
  class HeldSave(val result: SaveResult) {
    /** Completes when the held save has started. */
    val started: CompletableDeferred<Unit> = CompletableDeferred()

    /** Lets the held save return [result] when completed. */
    val release: CompletableDeferred<Unit> = CompletableDeferred()
  }

  /**
   * Returns the last slice that the vault saved through this provider for the [locality] layer of [context].
   *
   * @return the saved layer, or `null` when the vault saved nothing there
   */
  fun saved(context: SettingsContext, locality: LayerLocality): MapValue? = savedSlices[context.identity to locality]

  /**
   * Returns every layer seeded so far under [scopeContext], so a reload **installs the seeds again** and does not
   * sweep them. Without this, the one-time boot reload of the vault can sweep a seed pushed just after it. Only the
   * first seed-based test meets that, for example when a class runs alone. A load that [holdNextRead] holds waits
   * here after it reads the store.
   */
  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
    val layers = store.entries
      .filter { (coordinate, _) -> scopeContext.isAncestorOf(coordinate.first, strict = false) }
      .map { (coordinate, map) -> layerData(coordinate.first, coordinate.second, map) }
    heldReads.remove(scopeContext.identity)?.let { heldRead ->
      heldRead.storeRead.complete(Unit)
      heldRead.release.await()
      heldRead.failure?.let { throw it }
    }
    return layers
  }

  /**
   * Holds the next [requestData] of [scopeContext] after it reads the store and before it returns. A change seeded
   * meanwhile is then missing from the data that the load returns.
   *
   * @param failure the exception that the load throws when released, so the reload fails for this provider
   * @return the handle that reports the store read and releases the load
   */
  fun holdNextRead(scopeContext: SettingsContext, failure: RuntimeException? = null): HeldRead =
    HeldRead(failure).also { heldReads[scopeContext.identity] = it }

  /**
   * Reports that a held [requestData] read the store, and releases it. See [holdNextRead].
   *
   * @param failure the exception that the held load throws when released, or `null` to return the data
   */
  class HeldRead(val failure: RuntimeException? = null) {
    /** Completes when the held load has read the store. */
    val storeRead: CompletableDeferred<Unit> = CompletableDeferred()

    /** Lets the held load return when completed. */
    val release: CompletableDeferred<Unit> = CompletableDeferred()
  }

  /** Seeds the typed [value] for [key] into this provider's own [locality] layer for [context], nesting a slotted child as production would. */
  fun <T> seed(context: SettingsContext, key: SettingsKey<T>, value: T, locality: LayerLocality = LayerLocality.TEAM) {
    emit(context.realContext, locality) { it.with(context, key, value) }
  }

  /** Seeds the whole [locality] layer of [context] with [map] at once, for example a config parsed from JSON test data. */
  fun seed(context: SettingsContext, map: MapValue, locality: LayerLocality = LayerLocality.TEAM) {
    emit(context.realContext, locality) { map }
  }

  /**
   * Puts a raw serialized [value] under the setting identity [keyId] into the [locality] layer of [context], so a test
   * can seed a nested map.
   */
  fun put(context: SettingsContext, keyId: String, value: SettingsValue, locality: LayerLocality = LayerLocality.TEAM) {
    emit(context.realContext, locality) { current ->
      MapValue.Builder().apply {
        current.values.forEach { (id, v) -> this[id] = v }
        this[keyId] = value
      }.build()
    }
  }

  private fun emit(context: SettingsContext, locality: LayerLocality, update: (MapValue) -> MapValue) {
    val updated = store.compute(context to locality) { _, current -> update(current ?: MapValue(emptyMap())) }!!
    SettingsVault.getInstance().push(layerData(context, locality, updated))
  }

  private fun layerData(context: SettingsContext, locality: LayerLocality, map: MapValue): LayerData =
    LayerData.Builder(context, this, locality).apply { map.values.forEach { (id, v) -> this[id] = v } }.build()
}
