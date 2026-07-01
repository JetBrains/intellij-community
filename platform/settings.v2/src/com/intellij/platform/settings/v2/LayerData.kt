// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/layer-resolution.spec.md

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Holds the immutable data of one layer in the resolution stack of a context. A provider emits it. The vault stores
 * the [map] under `context -> (locality, provider)`. A context snapshot orders those maps by locality, then by
 * provider.
 *
 * @property context the context node that the layer applies to
 * @property provider the provider that produced the layer
 * @property locality the locality of the physical layer
 * @property map the values of the layer, by key identity
 */
@Experimental
class LayerData internal constructor(
  val context: SettingsContext,
  val provider: LayerProvider,
  val locality: LayerLocality,
  val map: SettingsValue.MapValue,
) {
  /** `true` when the layer holds no values. The vault treats such data as a removal of the entry for [context]. */
  val isEmpty: Boolean get() = map.isEmpty

  /** Collects the values for a [LayerData]. The built instance is read-only. */
  @Experimental
  class Builder(val context: SettingsContext, val provider: LayerProvider, val locality: LayerLocality) {
    private val map: SettingsValue.MapValue.Builder = SettingsValue.MapValue.Builder()

    /** Stores an already-serialized [value] under the setting identity [keyId]. This carries a key that is not loaded. */
    operator fun set(keyId: String, value: SettingsValue) {
      map[keyId] = value
    }

    /** Serializes [value] with [key]'s serializer and stores it under the key's [identity][SettingsKey.identity]. */
    operator fun <T> set(key: SettingsKey<T>, value: T) {
      map[key] = value
    }

    /** Returns the layer data of the values collected so far. */
    fun build(): LayerData = LayerData(context, provider, locality, map.build())
  }

  @Experimental
  companion object {
    /** Returns empty data for [context] from [provider] at [locality]. A provider emits it to remove that entry from the vault. */
    fun empty(context: SettingsContext, provider: LayerProvider, locality: LayerLocality): LayerData =
      LayerData(context, provider, locality, SettingsValue.EMPTY_MAP)
  }
}
