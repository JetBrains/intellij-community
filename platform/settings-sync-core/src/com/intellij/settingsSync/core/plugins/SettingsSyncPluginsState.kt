package com.intellij.settingsSync.core.plugins

import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState.PluginData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class SettingsSyncPluginsState(val plugins: Map<@Serializable(with= PluginIdSerializer::class) PluginId, PluginData>) {
  @Serializable
  data class PluginData(
    val enabled: Boolean = true,
    val category: SettingsCategory = SettingsCategory.PLUGINS,
    val dependencies: Set<String> = emptySet()
  )
}

internal object PluginIdSerializer : KSerializer<PluginId> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PluginId", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: PluginId) = encoder.encodeString(value.idString)
  override fun deserialize(decoder: Decoder): PluginId = PluginId.getId(decoder.decodeString())
}

internal object SettingsSyncPluginsStateMerger {
  /**
   * Merges two plugin states which were modified concurrently.
   * The changes which add or remove plugins (comparing to the base state) will be applied together.
   * If the same plugin is modified simultaneously, then the newer (last modified) state is preferred.
   */
  internal fun mergePluginStates(baseState: SettingsSyncPluginsState, olderState: SettingsSyncPluginsState, newerState: SettingsSyncPluginsState): SettingsSyncPluginsState {
    val result = baseState.plugins.toMutableMap()
    val diffBetweenBaseAndOld = calcDiff(baseState, olderState)
    val diffBetweenBaseAndNew = calcDiff(baseState, newerState)

    result += diffBetweenBaseAndOld.added
    result += diffBetweenBaseAndOld.modified
    result -= diffBetweenBaseAndOld.removed.keys

    result += diffBetweenBaseAndNew.added
    result += diffBetweenBaseAndNew.modified
    result -= diffBetweenBaseAndNew.removed.keys

    return SettingsSyncPluginsState(result)
  }

  private fun calcDiff(baseState: SettingsSyncPluginsState, state: SettingsSyncPluginsState): Diff {
    val added = state.plugins - baseState.plugins.keys
    val removed = baseState.plugins - state.plugins.keys
    val modified = state.plugins.filter { baseState.plugins.containsKey(it.key) }
    return Diff(added, removed, modified)
  }

  private class Diff(
    val added: Map<PluginId, PluginData>,
    val removed: Map<PluginId, PluginData>,
    val modified: Map<PluginId, PluginData>
  )
}
