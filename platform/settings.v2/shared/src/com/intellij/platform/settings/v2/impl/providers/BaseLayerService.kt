// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md
// @spec ../../../../../../../../../spec/layer-loading.spec.md

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SaveResult
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.SettingsDecodingException
import com.intellij.platform.settings.v2.impl.layerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Implements the shared part of the service that backs a [LayerProvider]. The service is a persistence port over one
 * storage format (ADR 0057). The vault owns the model and all the mutation logic. This base only loads the layer in
 * and writes it back out. A subclass supplies the parts that depend on the format:
 * - how to [read] and [write] the whole [MapValue] of a context;
 * - which localities [take a write][availableLocalities] at a context;
 * - the [providerClass] to tag the layer data with;
 * - which contexts a [requestData] scope covers.
 *
 * **The load path.** [load] ([read], then [split]) returns the layers of a storage unit. [requestData] gives them to
 * the vault to install. [refresh] pushes the same result as a spontaneous update when the subclass sees that its
 * source changed on its own. A malformed source gives no layer: a push keeps the installed state, and a reload drops
 * it (ADR 0145). [split] fans the whole model of a storage unit out into the layers of each context. By default it
 * gives the model as is. The file layer splits a directory config into the directory layer and the per-file layers.
 *
 * **The persist path.** The vault calls [writeData] with the model slices of a storage unit, grouped by
 * [storageContext]. The default writes the single slice. A subclass whose file backs several contexts joins them.
 */
abstract class BaseLayerService(private val scope: CoroutineScope) : Disposable {
  /**
   * Holds the layer localities that this service persists, each to its own physical store. By default it holds all
   * three. A service with fewer stores narrows it, such as the read-only application defaults. [load] reads every
   * one. The localities that take a write come from [availableLocalities].
   */
  open val localities: List<LayerLocality> = LayerLocality.entries

  /**
   * Loads the whole persisted layer of [storageContext] for [locality].
   *
   * @return the layer, or an empty map for an empty or absent layer
   * @throws SettingsDecodingException when the stored form does not parse, such as a malformed or half-edited file.
   *   [load] then gives no layer for that locality (ADR 0145).
   */
  protected abstract suspend fun read(storageContext: SettingsContext, locality: LayerLocality): MapValue

  /** Persists the whole layer [data] of [storageContext] for [locality] to the store of that locality. */
  protected abstract suspend fun write(storageContext: SettingsContext, locality: LayerLocality, data: MapValue)

  /**
   * Returns the [localities] that take a write at [context]. It fulfills [LayerProvider.availableLocalities], so it
   * gates every mutation: plain writes and slot operations alike. A read-only service and a context kind that the
   * service does not store both give an empty set (ADR 0129). Synchronous and side-effect-free, because the vault
   * calls it under its lock.
   *
   * @param context a context that this service backs
   * @return the localities that take a write at [context], or an empty set
   */
  abstract fun availableLocalities(context: SettingsContext): Set<LayerLocality>

  /**
   * Holds the class of the stateless, singleton provider that this service backs. [load] tags the layer data with
   * that provider (see [layerProvider]).
   */
  protected abstract val providerClass: Class<out LayerProvider>

  /**
   * Computes and returns the layer of every context that [scopeContext] covers, for the vault to install. A scope is a
   * root: the application, a project or a directory. The subclass knows the shape of its contexts, so it resolves the
   * covered contexts and [load]s each one. It fulfills [LayerProvider.requestData].
   */
  abstract suspend fun requestData(scopeContext: SettingsContext): List<LayerData>

  /**
   * Returns the context that owns the physical store where a write at [context] lands. The persistence coalesces on
   * this unit. By default it is the [real context][SettingsContext.realContext], so an
   * [access][com.intellij.platform.settings.v2.access] overlay resolves to its base. A subclass whose file backs
   * several contexts maps them all to the one owning directory context, so their slices are written together. See
   * [LayerProvider.storageContext].
   */
  open fun storageContext(context: SettingsContext): SettingsContext = context.realContext

  /**
   * Fans the whole [model] of [storageContext] out into the individual layers, each tagged with its context. By default
   * it gives one layer for the one context. A subclass whose model holds several contexts splits it, such as the
   * directory layer and one layer per file entry. It also gives empty layers to clear the entries that are gone.
   */
  protected open suspend fun split(storageContext: SettingsContext, locality: LayerLocality, model: MapValue): List<Pair<SettingsContext, MapValue>> =
    listOf(storageContext to model)

  /**
   * Persists the model the vault holds for [storageContext]: [slices] maps each context that stores here to its
   * whole layer. The default writes the single slice for [storageContext]; a subclass whose store backs several
   * contexts joins them into one on-disk form first. See [LayerProvider.writeData].
   *
   * @return [SaveResult.SAVED] once the slices are written, or [SaveResult.DROPPED] when a subclass cannot store them
   */
  open suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>): SaveResult {
    write(storageContext, locality, slices[storageContext] ?: MapValue(emptyMap()))
    return SaveResult.SAVED
  }

  /**
   * Logs a warning that the save of [storageContext] for [locality] is dropped, with the dropped [slices] as JSON
   * (ADR 0122).
   *
   * @param reason why the store has no place, as a clause that follows a colon, such as "it is not a directory on disk".
   */
  protected fun logDroppedSave(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>, reason: String) {
    val droppedSlices = JsonConfigFile.toJson(MapValue.wrapOwned(slices.mapKeys { (context, _) -> context.identity }))
    LOG.warn("Dropping the '$locality' settings of '${storageContext.identity}': $reason. The dropped settings: $droppedSlices")
  }

  /**
   * Reads the stored config of [storageContext] and [split]s it into the layers to return. [requestData] drives this
   * load path. A malformed file (a [SettingsDecodingException] from [read]) gives no layer for its locality. A reload
   * then drops the installed layers, and a push keeps them (ADR 0145). An empty or absent file gives an empty layer.
   * The result is empty when [providerClass] is not registered.
   */
  protected suspend fun load(storageContext: SettingsContext): List<LayerData> {
    val provider = layerProvider(providerClass) ?: return emptyList()
    return localities.flatMap { locality ->
      val data = try {
        read(storageContext, locality)
      }
      catch (e: SettingsDecodingException) {
        LOG.debug(e) { "Skipping '$locality' layer for '${storageContext.identity}': stored settings are malformed" }
        return@flatMap emptyList()
      }
      split(storageContext, locality, data).map { (layerContext, layer) -> layerData(layerContext, provider, layer, locality) }
    }
  }

  /**
   * Reads the stored config of [storageContext] again and pushes the layers to the vault as a spontaneous update. A
   * subclass calls it when it sees that its source changed on its own, however it detects that. [load], in contrast,
   * returns the layers for the vault to install. It uses [load], so it skips a malformed source the same way.
   */
  protected fun refresh(storageContext: SettingsContext) {
    scope.launch {
      val vault = SettingsVault.getInstance()
      for (data in load(storageContext)) vault.push(data)
    }
  }

  override fun dispose() {}

  private companion object {
    private val LOG = logger<BaseLayerService>()
  }
}
