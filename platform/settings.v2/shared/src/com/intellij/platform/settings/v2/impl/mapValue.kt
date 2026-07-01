// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/multidimensional-settings.spec.md

import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMapKey
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.UnpinnedDiscriminatorException

/**
 * Stores [key]'s [value] in a copy of this map.
 *
 * @param context pins the slot of every map key up [key]'s [SettingsKey.storageChain]
 * @param key a plain key, stored at the top level, or a child key, stored in its nested slot
 * @return a copy that holds the value, with the other entries preserved and every missing map created
 * @throws UnpinnedDiscriminatorException if [context] pins no slot for a map key in the chain
 */
internal fun <T> MapValue.with(context: SettingsContext, key: SettingsKey<T>, value: T): MapValue =
  splice(key.slotPath(context), key.identity, key.serializer.serialize(value))

/**
 * Collects the nested-map path to this key's slot, outermost first: `[mapKeyId, slot, …]`.
 *
 * @param context pins the slot each map key up the chain contributes
 * @return the path, or an empty list for a plain key
 * @throws UnpinnedDiscriminatorException if [context] pins no slot for a map key in the chain
 */
private fun SettingsKey<*>.slotPath(context: SettingsContext): List<String> =
  storageChain()
    .toList()
    .asReversed()
    .flatMap { mapKey: SettingsMapKey -> listOf(mapKey.identity, mapKey.pinnedDiscriminatorValue(context, this)) }

/**
 * Copies one whole slot of a storage. A slot is the discriminator-keyed sub-map a [SettingsMapKey] holds. The copy
 * moves it by identity, so the child keys inside it can stay unloaded.
 *
 * @param mapId the identity of the storage that holds the slots
 * @param from the slot to copy
 * @param to the slot to write, overwritten if it is already there
 * @return a copy that holds the slot at [to], or this map if the storage or the [from] slot is absent
 */
internal fun MapValue.copySubMap(mapId: String, from: String, to: String): MapValue {
  val slots = this[mapId] as? MapValue ?: return this
  val source = slots[from] ?: return this
  return MapValue.wrapOwned(values + (mapId to MapValue.wrapOwned(slots.values + (to to source))))
}

/**
 * Drops one slot of a storage. The storage entry itself goes with its last slot. See [copySubMap] for what a slot is.
 *
 * @param mapId the identity of the storage that holds the slots
 * @param value the slot to drop
 * @return a copy without the slot, or this map if the storage or the slot is absent
 */
internal fun MapValue.dropSubMap(mapId: String, value: String): MapValue {
  val slots = this[mapId] as? MapValue ?: return this
  if (slots[value] == null) return this
  val remaining = slots.values - value
  return if (remaining.isEmpty()) MapValue.wrapOwned(values - mapId) else MapValue.wrapOwned(values + (mapId to MapValue.wrapOwned(remaining)))
}

/**
 * Stores [value] at the end of [path], creating every missing map on the way down.
 *
 * @param path the map identities to descend, outermost first
 * @param leafId the identity to store [value] under
 * @return a copy that holds the value, with the siblings at every level preserved
 */
private fun MapValue.splice(path: List<String>, leafId: String, value: SettingsValue): MapValue {
  if (path.isEmpty()) return MapValue.wrapOwned(values + (leafId to value))
  val head = path.first()
  val child = this[head] as? MapValue ?: SettingsValue.EMPTY_MAP
  // `values + pair` copies each level once; a Builder would copy it a second time in build()
  return MapValue.wrapOwned(values + (head to child.splice(path.drop(1), leafId, value)))
}
