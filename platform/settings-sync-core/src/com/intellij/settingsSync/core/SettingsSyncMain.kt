package com.intellij.settingsSync.core

import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
fun isSettingsSyncEnabledInSettings(): Boolean = SettingsSyncSettings.getInstance().syncEnabled

/**
 * `false` when the product does not sync plugins, for example Draft.
 * Such a product neither applies the synced plugin state nor sends its own, so the plugins of the other IDEs stay as they are.
 * The flag is a product property and not a category state, because the category state syncs to the other IDEs.
 */
internal val isPluginSyncSupported: Boolean
  get() = SystemProperties.getBooleanProperty("settings.sync.plugins.supported", true)

internal const val SETTINGS_SYNC_STORAGE_FOLDER: String = "settingsSync"

@ApiStatus.Internal
@Service
class SettingsSyncMain(coroutineScope: CoroutineScope, appConfigPath: Path) : Disposable {
  constructor(coroutineScope: CoroutineScope) : this(coroutineScope, PathManager.getConfigDir())
  val controls: SettingsSyncControls

  init {
    val componentStore = ApplicationManager.getApplication().stateStore as ComponentStoreImpl
    val ideMediator = SettingsSyncIdeMediatorImpl(componentStore = componentStore, rootConfig = appConfigPath, enabledCondition = {
      isAvailable() && isSettingsSyncEnabledInSettings()
    })
    controls = init(coroutineScope,
                    parentDisposable = this,
                    settingsSyncStorage = appConfigPath.resolve(SETTINGS_SYNC_STORAGE_FOLDER),
                    appConfigPath = appConfigPath,
                    ideMediator = ideMediator)
  }

  override fun dispose() {
  }

  fun disableSyncing() {
    controls.ideMediator.removeStreamProvider()
  }

  companion object {
    fun isAvailable(): Boolean {
      return ApplicationManager.getApplication().serviceIfCreated<SettingsSyncMain>() != null
    }

    fun getInstance(): SettingsSyncMain = service<SettingsSyncMain>()
    suspend fun getInstanceAsync(): SettingsSyncMain = serviceAsync<SettingsSyncMain>()

    // Extracted to simplify testing, otherwise it is fast and is called from the service initializer
    internal fun init(
      coroutineScope: CoroutineScope,
      parentDisposable: Disposable,
      settingsSyncStorage: Path,
      appConfigPath: Path,
      ideMediator: SettingsSyncIdeMediator,
    ): SettingsSyncControls {
      val settingsLog = GitSettingsLog(settingsSyncStorage, appConfigPath, parentDisposable,
                                       {
                                         val userId = RemoteCommunicatorHolder.getRemoteCommunicator()?.userId ?: return@GitSettingsLog null
                                         RemoteCommunicatorHolder.getAuthService()?.getUserData(userId)
                                       },
                                       pluginStateFromCloudOnly = { !ideMediator.pluginSyncSupported },
                                       initialSnapshotProvider = { currentSnapshot ->
                                         ideMediator.getInitialSnapshot(appConfigPath, currentSnapshot)
                                       })
      val updateChecker = SettingsSyncUpdateChecker()
      val bridge = SettingsSyncBridge(coroutineScope, appConfigPath, settingsLog, ideMediator, updateChecker)
      return SettingsSyncControls(ideMediator, updateChecker, bridge, settingsSyncStorage)
    }
  }

  class SettingsSyncControls(val ideMediator: SettingsSyncIdeMediator,
                             val updateChecker: SettingsSyncUpdateChecker,
                             val bridge: SettingsSyncBridge,
                             val settingsSyncStorage: Path)
}
