// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-loading.spec.md
// @spec ../../../../../../spec/layer-resolution.spec.md
// @spec ../../../../../../spec/settings-writes.spec.md

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Stores every layer for the whole application. It is the single source of truth for settings in memory. On a
 * [reload], it asks each [LayerProvider] for its data. A provider [push]es a later change that it sees in its own
 * store. A read takes an ordered [snapshot] of the layers of a context. A write and a slot operation go through here.
 *
 * This interface is in the API module, so the public entry points can reach it. `SettingsVaultImpl` in
 * `intellij.platform.settings.v2.shared` implements it as an application service.
 */
@Internal
interface SettingsVault {
  /**
   * Returns the layer maps of [context] that resolve [key], **narrowest first**. The contexts go from [context] to
   * the application. Within each context, the maps follow the locality precedence
   * ([LayerLocality.byResolutionPrecedence]). Within each locality, the narrowest provider comes first. So the first
   * map that holds [key] is the winning layer. A caller takes the first hit (see [SettingsKey.resolveRaw]), or
   * reverses the list for a widest-first fold (see [SettingsKey.values]).
   *
   * The list skips a chain node that the [contextFilter][SettingsKey.contextFilter] of [key] rejects, and a locality
   * that its [locality][SettingsKey.locality] rejects. It also skips a provider that the
   * [layerFilter][SettingsKey.layerFilter] of [key] rejects, or whose [key filter][LayerProvider.acceptsKey] rejects
   * [key].
   */
  fun snapshot(context: SettingsContext, key: SettingsKey<*>): List<SettingsValue.MapValue>

  /**
   * Folds the [data] of one provider into the store. An empty [data] removes the entry of its context for that
   * provider. A push whose store is in a [dirty][isDirty] context applies when that context gets ready (ADR 0123,
   * ADR 0160). The vault drops a push while a save of its store is pending.
   */
  fun push(data: LayerData)

  /**
   * Requests the data of **every** provider for [scopeContext] ([LayerProvider.requestData]) and installs it. The
   * vault runs it at startup, and `ProjectSettingsLoadActivity` runs it at a project open. A provider that registers
   * later loads on its own, not through here.
   *
   * It suspends until the data of every provider is fetched **and installed**, so on return the loaded values are in
   * the model. A layer with a pending save keeps its unsaved value. The vault widens [scopeContext] to its widest
   * [LayerProvider.storageContext], so a reload never covers only a part of a store (ADR 0115).
   */
  suspend fun reload(scopeContext: SettingsContext)

  /**
   * Returns whether the resolved value of [context] may still be stale. It is stale while a running or a queued
   * reload of [context] or of an ancestor has not installed the bucket of the kind of [context] or of a wider kind
   * (ADR 0160). See [SettingsContext.isDirty].
   */
  fun isDirty(context: SettingsContext): Boolean

  /**
   * Suspends until [context] is no longer [dirty][isDirty], so every covering reload has installed the buckets that
   * [context] reads. See [SettingsContext.awaitReady].
   */
  suspend fun awaitReady(context: SettingsContext)

  /**
   * Writes to the disk **now** every pending debounced save of a store that [scopeContext] covers, and suspends until
   * they are all written. Use it where a deferred save would be lost or must be visible at once: the application
   * shutdown, a project close, a save before a commit or a branch switch. Only the persistence is forced. The model in
   * memory is already current, so a reader sees no change. The caller must stop the source of writes first, because a
   * write after the flush starts may not be included.
   */
  suspend fun flush(scopeContext: SettingsContext)

  /** Writes [value] for [key] at [context] or the nearest parent that takes it. Backs [SettingsKey.set], which holds the contract. */
  @Throws(ReadOnlySettingsException::class, SettingsValidationException::class)
  suspend fun <T> set(context: SettingsContext, key: SettingsKey<T>, value: T)

  /**
   * Copies the [from] slot of [mapKey] to [to] at [context]. It **broadcasts** the change to every provider and
   * locality that holds the slot there, and each one rewrites its own storage. A slot operation is an edit of the
   * whole scope, not a routed write. So it matches [context] **directly**, with no walk up the chain, and it reaches
   * all the layers, not one.
   *
   * It skips a layer that cannot take it, such as a read-only layer. It leaves a layer without the [from] slot as it
   * is, and it overwrites an existing [to] slot. It does nothing when no layer holds the [from] slot.
   */
  suspend fun copySlot(context: SettingsContext, mapKey: SettingsMapKey, from: String, to: String)

  /** Drops the [value] slot of [mapKey] at [context], in every provider and locality that holds it there. See [copySlot]. */
  suspend fun dropSlot(context: SettingsContext, mapKey: SettingsMapKey, value: String)

  /**
   * Renames the [from] slot of [mapKey] to [to] at [context], in every provider and locality that holds it there. It
   * applies a copy and a drop as one atomic batch (ADR 0132). See [copySlot].
   */
  suspend fun renameSlot(context: SettingsContext, mapKey: SettingsMapKey, from: String, to: String)

  /** Starts an atomic mutation with [context] as the default context of its entries. See [SettingsMutationBuilder] and [SettingsContext.mutate]. */
  fun mutation(context: SettingsContext): SettingsMutationBuilder

  /** Registers a key-only [listener], which ends when [disposable] is disposed. Backs [SettingsKey.addListener], which holds the contract. */
  fun addListener(key: SettingsKey<*>, disposable: Disposable, listener: SettingsChangeListener)

  /**
   * Registers a [listener] of the stored value that [key] resolves in [context], which ends when [disposable] is
   * disposed. Backs [SettingsKey.addListener], which holds the contract.
   */
  fun addListener(key: SettingsKey<*>, context: SettingsContext, disposable: Disposable, listener: SettingsChangeListener)

  companion object {
    fun getInstance(): SettingsVault = service<SettingsVault>()
  }
}
