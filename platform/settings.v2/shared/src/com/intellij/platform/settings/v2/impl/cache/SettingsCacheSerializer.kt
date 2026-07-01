// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.cache

// @spec ../../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheSerializer.VERSION
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Holds one cached layer: the [value] of a single (context, provider, locality) layer, with enough data to rebuild its
 * address on a load.
 *
 * @property contextId the [identity][com.intellij.platform.settings.v2.SettingsContext.identity] of the context, which
 *   [ContextFactory.deserialize][com.intellij.platform.settings.v2.ContextFactory.deserialize] rebuilds
 * @property providerId the class name of the provider, which resolves against the live extension list
 * @property locality the name of the [LayerLocality][com.intellij.platform.settings.v2.LayerLocality] entry
 */
@Serializable
internal data class CachedLayer(
  val contextId: String,
  val providerId: String,
  val locality: String,
  val value: MapValue,
)

/** Holds the on-disk root: a [version] guard and the cached [layers] of one scope. */
@Serializable
private data class CachedModel(val version: Int, val layers: List<CachedLayer>)

/**
 * Encodes and decodes a scope's cached layers for the snapshot cache. The cache is a pure accelerator, so a stale or
 * unreadable payload is never fatal: [decode] returns `null` on any version mismatch or malformed content, and the
 * caller rebuilds from the real providers. Bump [VERSION] whenever [CachedLayer]'s shape or a context identity layout
 * changes so old caches are dropped instead of mis-parsed (a binary format has no field names to fall back on).
 */
@OptIn(ExperimentalSerializationApi::class)
internal object SettingsCacheSerializer {
  private const val VERSION: Int = 3
  private val LOG = logger<SettingsCacheSerializer>()
  private val protoBuf = ProtoBuf { encodeDefaults = true }

  /** Returns the bytes that hold [layers] under the current [VERSION]. */
  fun encode(layers: List<CachedLayer>): ByteArray = protoBuf.encodeToByteArray(CachedModel(VERSION, layers))

  /** Returns the decoded layers, or `null` for a different [VERSION] or a malformed content. Both cases are logged. */
  fun decode(bytes: ByteArray): List<CachedLayer>? =
    try {
      val model = protoBuf.decodeFromByteArray<CachedModel>(bytes)
      if (model.version == VERSION) model.layers
      else {
        LOG.info("Ignoring settings cache: version ${model.version} != $VERSION")
        null
      }
    }
    catch (e: SerializationException) {
      LOG.warn("Ignoring malformed settings cache", e)
      null
    }
}
