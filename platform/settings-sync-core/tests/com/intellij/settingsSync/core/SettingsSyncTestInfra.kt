package com.intellij.settingsSync.core

import com.intellij.configurationStore.getDefaultStoragePathSpec
import com.intellij.configurationStore.serializeStateInto
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.core.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState.PluginData
import com.intellij.util.toByteArray
import com.intellij.util.xmlb.Constants
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.junit.Assert.assertEquals
import java.nio.charset.StandardCharsets
import java.time.Instant

@ApiStatus.Internal
fun SettingsSnapshot.assertSettingsSnapshot(buildExpectedSnapshot: SettingsSnapshotBuilder.() -> Unit) {
  val settingsSnapshotBuilder = SettingsSnapshotBuilder()
  settingsSnapshotBuilder.buildExpectedSnapshot()
  val expectedSnapshot = SettingsSnapshot(metaInfo, settingsSnapshotBuilder.fileStates,
                                          SettingsSyncPluginsState(settingsSnapshotBuilder.plugins),
                                          settingsSnapshotBuilder.settingsFromProviders, settingsSnapshotBuilder.additionalFiles)
  assertSettingsSnapshotsEqual(expectedSnapshot, this)
}

internal fun assertSettingsSnapshotsEqual(expectedSnapshot: SettingsSnapshot, actualSnapshot: SettingsSnapshot) {
  assertFileStates(expectedSnapshot.fileStates, actualSnapshot.fileStates)
  assertFileStates(expectedSnapshot.additionalFiles, actualSnapshot.additionalFiles)
  assertEquals(expectedSnapshot.plugins?.plugins, actualSnapshot.plugins?.plugins ?: emptyMap<PluginId, PluginData>())
  assertEquals("Unexpected settings from providers", expectedSnapshot.settingsFromProviders, actualSnapshot.settingsFromProviders)
}

private fun assertFileStates(expectedFileStates: Collection<FileState>, actualFileStates: Collection<FileState>) {
  val transformation = { fileState: FileState ->
    val content = if (fileState is FileState.Modified) String(fileState.content, StandardCharsets.UTF_8) else DELETED_FILE_MARKER
    fileState.file to content
  }
  val actualMap = actualFileStates.associate(transformation).toSortedMap()
  val expectedMap = expectedFileStates.associate(transformation).toSortedMap()
  if (actualMap != expectedMap) {
    val missingKeys = expectedMap.keys - actualMap.keys
    val extraKeys = actualMap.keys - expectedMap.keys
    val message = StringBuilder()
    if (missingKeys.isNotEmpty()) message.append("Missing settings file: $missingKeys\n")
    if (extraKeys.isNotEmpty()) message.append("Extra settings file: $extraKeys\n")
    assertEquals("Incorrect snapshot: $message", expectedMap, actualMap)
  }
}

internal fun PersistentStateComponent<*>.toFileState() : FileState {
  val file = PathManager.OPTIONS_DIRECTORY + "/" + getDefaultStoragePathSpec(this::class.java)
  val content = this.serialize()
  return FileState.Modified(file, content)
}

internal val <T> PersistentStateComponent<T>.name: String
  get() = (this::class.annotations.find { it is State } as? State)?.name!!

internal fun PersistentStateComponent<*>.serialize(): ByteArray {
  val compElement = Element("component")
  compElement.setAttribute(Constants.NAME, this.name)
  serializeStateInto(this, compElement)

  val appElement = Element("application")
  appElement.addContent(compElement)
  return appElement.toByteArray()
}

internal fun settingsSnapshot(metaInfo: MetaInfo = MetaInfo(Instant.now(), getLocalApplicationInfo()),
                              build: SettingsSnapshotBuilder.() -> Unit) : SettingsSnapshot {
  val builder = SettingsSnapshotBuilder()
  builder.build()
  return SettingsSnapshot(metaInfo, builder.fileStates.toSet(),
                          if (builder.pluginInformationExists) SettingsSyncPluginsState(builder.plugins) else null,
                          builder.settingsFromProviders,
                          builder.additionalFiles.toSet())
}

@ApiStatus.Internal
class SettingsSnapshotBuilder {
  val settingsFromProviders = mutableMapOf<String, Any>()
  val fileStates = mutableSetOf<FileState>()
  val plugins = mutableMapOf<PluginId, PluginData>()
  var pluginInformationExists = false
  val additionalFiles = mutableSetOf<FileState>()

  fun fileState(function: () -> PersistentStateComponent<*>) {
    val component : PersistentStateComponent<*> = function()
    fileStates.add(component.toFileState())
  }

  fun fileState(fileState: FileState): FileState {
    fileStates.add(fileState)
    return fileState
  }

  fun fileState(file: String, content: String): FileState {
    val byteArray = content.toByteArray()
    return fileState(FileState.Modified(file, byteArray))
  }

  fun plugin(id: String,
             enabled: Boolean = true,
             category: SettingsCategory = SettingsCategory.PLUGINS,
             dependencies: Set<String> = emptySet()): Pair<PluginId, PluginData> {
    pluginInformationExists = true
    val pluginId = PluginId.getId(id)
    val pluginData = PluginData(enabled, category, dependencies)
    plugins[pluginId] = pluginData
    return pluginId to pluginData
  }

  fun additionalFile(file: String, content: String) {
    additionalFiles += FileState.Modified(file, content.toByteArray())
  }

  fun provided(id: String, state: Any) {
    settingsFromProviders[id] = state
  }
}
