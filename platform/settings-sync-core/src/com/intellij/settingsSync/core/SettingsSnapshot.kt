package com.intellij.settingsSync.core

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.JDOMUtil
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState
import com.intellij.util.SystemProperties
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * @param metaInfo Meta-information about this snapshot: when and where it was in created, etc.
 *
 * @param fileStates Files containing IDE settings. These files reflect the file structure of IDE settings
 * (e.g. `options/editor.xml` or `keymaps/mykeymap.xml`), they are copied to their places in the `APP_CONFIG` to update the state of the IDE.
 * The paths are relative to the `APP_CONFIG` folder (or to the `settingsSync/` folder, which is the same, because the folder reflects
 * the structure of `APP_CONFIG`).
 *
 * @param plugins The state of plugins. If the snapshot is being sent to the server, `plugins` contain the plugin state of the current IDE;
 * if the snapshot is received by this IDE, this state is applied to the current IDE.
 * `null` means there is no information about plugins in this snapshot.
 *
 * @param settingsFromProviders Settings from [SettingsProvider]s.
 * It is a map from provider's `id` to the state class holding actual settings.
 *
 * @param additionalFiles Additional files which don't directly represent IDE settings, they are placed into the `settingsSync/.metainfo`
 * folder, are stored in the history, and are sent back to the server. These files can be not processed by the Setting Sync, but they are
 * preserved during synchronization.
 */
@ApiStatus.Internal
data class SettingsSnapshot(val metaInfo: MetaInfo,
                            val fileStates: Set<FileState>,
                            val plugins: SettingsSyncPluginsState?,
                            val settingsFromProviders: Map</*SettingsProvider ID*/ String, Any>,
                            val additionalFiles: Set<FileState>) {

  data class MetaInfo(val dateCreated: Instant, val appInfo: AppInfo?, val isDeleted: Boolean = false)

  data class AppInfo(
    val applicationId: UUID,
    val buildNumber: BuildNumber?,
    val fullApplicationName: String?,
    val userName: String,
    val hostName: String,
    val configFolder: String)

  fun isEmpty(): Boolean = fileStates.isEmpty()
                           && (plugins == null || plugins.plugins.isEmpty())
                           && settingsFromProviders.isEmpty()
                           && additionalFiles.isEmpty()

  fun isDeleted(): Boolean {
    return metaInfo.isDeleted
  }

  fun getState(): SettingsSyncState {
    val fileState = fileStates.firstOrNull {
      it.file == ("${PathManager.OPTIONS_DIRECTORY}/${SettingsSyncSettings.FILE_SPEC}")
    } ?: return SettingsSyncStateHolder()

    if (fileState !is FileState.Modified) {
      return SettingsSyncStateHolder()
    }
    try {
      val componentElement = JDOMUtil.load(fileState.content).getChildren("component")
                               .firstOrNull { it.getAttributeValue("name") == SettingsSyncSettings.COMPONENT_NAME }
                             ?: return SettingsSyncStateHolder()
      val state = XmlSerializer.deserialize(componentElement, SettingsSyncSettings.State::class.java)
      return SettingsSyncStateHolder(
        state
      )
    }
    catch (e: Throwable) {
      logger<SettingsSyncSettings>().error("Unable to deserialize content of ${SettingsSyncSettings.FILE_SPEC} into object", e)
      return SettingsSyncStateHolder()
    }
  }

  companion object{
    fun extractFromZip(zipFile: Path): SettingsSnapshot? {
      return SettingsSnapshotZipSerializer.extractFromZip(zipFile)
    }
  }
}

@ApiStatus.Internal
fun getLocalApplicationInfo(): SettingsSnapshot.AppInfo {
  return SettingsSnapshot.AppInfo(SettingsSyncLocalSettings.getInstance().applicationId,
                                  ApplicationInfo.getInstance().build,
                                  ApplicationInfo.getInstance().fullApplicationName,
                                  SystemProperties.getUserName(),
                                  service<LocalHostNameProvider>().getHostName(),
                                  PathManager.getConfigPath())
}
