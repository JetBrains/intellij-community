// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/multidimensional-settings.spec.md

import com.intellij.platform.settings.v2.SettingsValue.Companion.EMPTY_MAP
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

/**
 * Declares **slotted storage**, a namespace that a [discriminatorKey] key slices. It serves multidimensional
 * settings, such as the inspections of each profile or the code style of each language.
 *
 * ```
 * // declaration
 * val CURRENT_PROFILE = SettingsKey<String>("current.profile", default = "default")
 * val INSPECTIONS_DATA = SettingsMapKey(name = "inspections.data", discriminator = CURRENT_PROFILE)
 * val INSPECTION1_ENABLED = SettingsKey<Boolean>("inspection1.enabled", storageKey = INSPECTIONS_DATA, default = true)
 *
 * // read a child for the currently selected profile
 * val enabled = INSPECTION1_ENABLED[context]
 *
 * // read the same child against a specific profile by pinning the discriminator
 * val enabledInRelease = INSPECTION1_ENABLED[context.access { this[CURRENT_PROFILE] = "release" }]
 * ```
 *
 * A child key names its [SettingsMapKey] in [SettingsKey.storageKey]. A read of the child resolves the
 * [discriminatorKey] through the layer stack, then reads the matching slot of the storage of the map key. When no
 * layer holds the slot, the read returns the default of the child. Its default factory learns the slot path with
 * [accessFor].
 *
 * A map key has **no** [layerFilter][SettingsKey.layerFilter], [contextFilter][SettingsKey.contextFilter] or
 * [validator][SettingsKey.validator]. The resolution applies the filters of the *child* key to this storage, so a
 * filter on the map key would never take effect. A map key is never written directly, so it has nothing to
 * validate. The filters and the validation belong on the value keys.
 */
@Experimental
class SettingsMapKey(
  name: String,
  /** The key whose resolved value selects the slot of this storage. */
  val discriminatorKey: SettingsKey<String>,
  /** The enclosing map key when this one is nested in a slot of another one, or `null` for a top-level map key. */
  storageKey: SettingsMapKey? = null,
) : SettingsKey<MapValue>(
  name = name,
  default = EMPTY_MAP,
  serializer = DumbMapValueSerializer,
  storageKey = storageKey,
) {
  /** The storage identity, marked with [STORAGE_IDENTITY_PREFIX]. So a layer node that holds the slots of this key differs from a plain value. */
  override val identity: String by lazy { STORAGE_IDENTITY_PREFIX + rawIdentity(storagePrefix, name) }

  /**
   * Fails always: a map key holds slots, not a value, so it is never read directly. Read a child key, which resolves
   * its slot through [subStorage].
   *
   * @throws UnsupportedOperationException on every call
   */
  override fun resolve(context: SettingsContext): MapValue {
    throw UnsupportedOperationException("SettingsMapKey should not be used directly")
  }

  /** Makes [pluginId] the owner of this map key, and returns it. See [SettingsKey.withTestPluginId]. */
  @TestOnly
  override fun withTestPluginId(pluginId: PluginId): SettingsMapKey {
    super.withTestPluginId(pluginId)
    return this
  }

  /**
   * Returns the selected slot of this map key in each resolution layer, in stack order. The [discriminatorValue] in
   * [context] selects the slot. A layer that holds no such slot gives no entry. The layers come from the [subStorage]
   * of the parent map key when this key is nested, else from the top-level snapshot of the context.
   *
   * @param filterKey the leaf key of the read, whose filters apply
   */
  fun subStorage(context: SettingsContext, filterKey: SettingsKey<*>): List<MapValue> {
    val unresolvedStorages = storage(context, filterKey).mapNotNull { it[this] as? MapValue }
    if (unresolvedStorages.isEmpty()) {
      return emptyList()
    }
    val discriminatorValue = discriminatorValue(context)
    return unresolvedStorages.mapNotNull { it[discriminatorValue] as? MapValue }
  }

  /**
   * Returns the [discriminatorKey]'s value resolved in [context], which selects the slot of this storage. The
   * value is the one [access] pins, or else the one the layers hold. The slot does not have to exist. A write
   * uses [pinnedDiscriminatorValue].
   */
  @Internal
  fun discriminatorValue(context: SettingsContext): String = context.discriminator(discriminatorKey) ?: discriminatorKey[context]

  /**
   * Returns the [discriminatorKey]'s value pinned in [context] by [access], which selects the slot a write of
   * [writtenKey] goes into. See [discriminatorValue] for the read side.
   *
   * @throws UnpinnedDiscriminatorException if [context] pins no value for [discriminatorKey].
   */
  @Internal
  fun pinnedDiscriminatorValue(context: SettingsContext, writtenKey: SettingsKey<*>): String =
    context.discriminator(discriminatorKey) ?: throw UnpinnedDiscriminatorException(writtenKey, this)

  /**
   * Copies the [from] slot to [to] at [context], for example to duplicate an inspection profile. The copy goes to
   * every locality and provider that holds the slot at [context]. See [SettingsVault.copySlot].
   */
  suspend fun copySlot(context: SettingsContext, from: String, to: String) {
    SettingsVault.getInstance().copySlot(context, this, from, to)
  }

  /**
   * Drops the [value] slot at [context], for example when the user deletes an inspection profile. The drop goes to
   * every locality and provider that holds the slot there. See [SettingsVault.dropSlot].
   */
  suspend fun dropSlot(context: SettingsContext, value: String) {
    SettingsVault.getInstance().dropSlot(context, this, value)
  }

  /**
   * Renames the [from] slot to [to] at [context], for example when the user renames an inspection profile. The
   * rename goes to every locality and provider that holds the slot there. It moves only the slot *data*. A context
   * whose stored discriminator still points at [from] is the concern of the owner, because there is no reference
   * integrity. See [SettingsVault.renameSlot].
   */
  suspend fun renameSlot(context: SettingsContext, from: String, to: String) {
    SettingsVault.getInstance().renameSlot(context, this, from, to)
  }
}
