package com.intellij.settingsSync.core.communicator

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.DynamicPlugins.loadPlugin
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.Path

@ApiStatus.Internal
object RemoteCommunicatorHolder : SettingsSyncEventListener {
  private val logger = logger<RemoteCommunicatorHolder>()
  const val DEFAULT_PROVIDER_CODE = "jba"
  const val DEFAULT_USER_ID = "jba"

  init {
    SettingsSyncEvents.getInstance().addListener(this)
  }

  // pair userId:remoteCommunicator
  @Volatile
  private var currentCommunicator: SettingsSyncRemoteCommunicator? = null

  fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator? = currentCommunicator ?: run {
    currentCommunicator = createRemoteCommunicator()
    return@run currentCommunicator
  }

  fun getAuthService() = getCurrentProvider()?.authService

  fun isAvailable() = currentCommunicator != null

  fun invalidateCommunicator() {
    currentCommunicator?.let {
      logger.info("Invalidating remote communicator")
      if (it is Disposable) {
        Disposer.dispose(it)
      }
    }
    currentCommunicator = null
  }

  fun getCurrentUserData(): SettingsSyncUserData? {
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: run {
      logger.warn("No current userId. Returning null user data")
      return null
    }
    val provider: SettingsSyncCommunicatorProvider = getCurrentProvider() ?: run {
      logger.warn("No active provider. Returning null user data")
      return null
    }
    return provider.authService.getUserData(userId)
  }

  override fun loginStateChanged() {
    invalidateCommunicator()
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (!syncEnabled) {
      invalidateCommunicator()
    }
  }

  fun isPendingAction(): Boolean {
    if (!SettingsSyncSettings.getInstance().syncEnabled)
      return false
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: return false
    return getCurrentProvider()?.authService?.getPendingUserAction(userId) != null
  }

  fun createRemoteCommunicator(provider: SettingsSyncCommunicatorProvider,
                               userId: String,
                               parentDisposable: Disposable? = null): SettingsSyncRemoteCommunicator? {
    val communicator: SettingsSyncRemoteCommunicator = provider.createCommunicator(userId) ?: return null
    if (parentDisposable != null && communicator is Disposable) {
      Disposer.register(parentDisposable, communicator)
    }
    return communicator
  }

  private fun createRemoteCommunicator(): SettingsSyncRemoteCommunicator? {
    val provider: SettingsSyncCommunicatorProvider = getCurrentProvider() ?: run {
      logger.warn("Attempting to create remote communicator without active provider")
      resetLoginData()
      return null
    }
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: run {
      logger.warn("Empty current userId. Communicator will not be created.")
      resetLoginData()
      return null
    }

    val currentUserData: SettingsSyncUserData = provider.authService.getUserData(userId) ?: run {
      logger.warn("Cannot find user data for user '$userId' in provider '${provider.providerCode}. Resetting the data'")
      resetLoginData()
      return null
    }
    val communicator: SettingsSyncRemoteCommunicator = provider.createCommunicator(currentUserData.id) ?: run {
      logger.warn("Provider '${provider.providerCode}' returned empty communicator")
      return null
    }
    return communicator
  }

  private fun resetLoginData() {
    logger.warn("Backup & Sync will be disabled.")
    SettingsSyncSettings.getInstance().syncEnabled = false
    SettingsSyncLocalSettings.getInstance().providerCode = null
    SettingsSyncLocalSettings.getInstance().userId = null
  }

  fun getAvailableProviders(): List<SettingsSyncCommunicatorProvider> {
    val extensionList = arrayListOf<SettingsSyncCommunicatorProvider>()
    extensionList.addAll(SettingsSyncCommunicatorProvider.PROVIDER_EP.extensionList.filter { it.isAvailable() })
    if (extensionList.find { it.providerCode == DEFAULT_PROVIDER_CODE } == null) {
      extensionList.add(DelegatingDefaultCommunicatorProvider)
    }
    return extensionList
  }

  fun getExternalProviders(): List<SettingsSyncCommunicatorProvider> {
    return getAvailableProviders().filter { it.providerCode != DEFAULT_PROVIDER_CODE }
  }

  fun getDefaultProvider(): SettingsSyncCommunicatorProvider? {
    return getProvider(DEFAULT_PROVIDER_CODE)
  }

  fun getProvider(providerCode: String): SettingsSyncCommunicatorProvider? {
    return getAvailableProviders().find { it.providerCode == providerCode }
  }

  fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    return getAvailableProviders().flatMap { it.authService.getAvailableUserAccounts() }
  }

  private fun getCurrentProvider(): SettingsSyncCommunicatorProvider? {
    val providerCode = SettingsSyncLocalSettings.getInstance().providerCode ?: return null
    return getProvider(providerCode)
  }

  private object DummyAuthService : SettingsSyncAuthService {
    override val providerCode = DEFAULT_PROVIDER_CODE
    override val providerName = "JetBrains"
    override val icon = AllIcons.Ultimate.IdeaUltimatePromo

    override suspend fun login(parentComponent: Component?): SettingsSyncUserData? {
      val marketplacePluginId = PluginId.getId("com.intellij.marketplace")
      val settingsSyncPluginId = PluginId.getId("com.intellij.settingsSync")
      val downloaders = hashMapOf<PluginId, PluginDownloader>()
      withModalProgress(ModalTaskOwner.guess(),
                        SettingsSyncBundle.message("settings.jba.plugin.download"),
                        TaskCancellation.cancellable()) {
        val pluginUpdates = MarketplaceRequests.getLastCompatiblePluginUpdate(setOf(marketplacePluginId, settingsSyncPluginId))
        for (update in pluginUpdates) {
          val pluginDescriptor = MarketplaceRequests.loadPluginDescriptor(update.pluginId, update)
          val downloader = PluginDownloader.createDownloader(pluginDescriptor)
          if (downloader.prepareToInstall(null)) {
            downloaders[downloader.id] = downloader
          }
        }
      }
      var installSuccessful: Boolean = withContext(Dispatchers.EDT) {
        if (!PluginManagerCore.isPluginInstalled(marketplacePluginId)) {
          val marketplacePluginDownloader = downloaders[marketplacePluginId] ?: let{
            logger.error("Cannot download plugin '${marketplacePluginId.idString}' from marketplace!")
            return@withContext false
          }
          val marketplacePluginFile = marketplacePluginDownloader.filePath
          val targetFile = PluginInstaller.unpackPlugin(marketplacePluginFile, Path.of(PathManager.getPluginsPath()))

          val targetDescriptor = loadDescriptor(targetFile, false, PluginXmlPathResolver.DEFAULT_PATH_RESOLVER) ?: let {
            logger.error("No descriptor found in marketplace plugin")
            return@withContext false
          }

          targetDescriptor.jarFiles = getJars(targetFile.parent)
          if (!loadPlugin(targetDescriptor)) {
            logger.error("Cannot load marketplace plugin")
            return@withContext false
          }
          SettingsSyncEvents.getInstance().fireRestartRequired(RestartForPluginInstall(setOf(marketplacePluginDownloader.pluginName)))
        }

        if (!PluginManagerCore.isPluginInstalled(settingsSyncPluginId)) {
          val syncPluginDownloader = downloaders[settingsSyncPluginId] ?: let{
            logger.error("Cannot download plugin '${settingsSyncPluginId.idString}' from marketplace!")
            return@withContext false
          }

          val syncPluginFile = syncPluginDownloader.filePath
          val syncPluginDescriptor = loadDescriptorFromArtifact(syncPluginFile, null) ?: let {
            logger.error("Cannot load plugin descriptor for ${settingsSyncPluginId.idString}")
            return@withContext false
          }
          if (!PluginInstaller.installAndLoadDynamicPlugin(syncPluginFile, null, syncPluginDescriptor)) {
            logger.error("Cannot load plugin ${settingsSyncPluginId.idString}")
            return@withContext false
          }
        }
        return@withContext true
      }
      if (!installSuccessful) {
        if (parentComponent != null) {
          Messages.showInfoMessage(parentComponent, SettingsSyncBundle.message("settings.jba.plugin.required.text"),
                                   SettingsSyncBundle.message("settings.jba.plugin.required.title"))
        }
        else {
          Messages.showInfoMessage(SettingsSyncBundle.message("settings.jba.plugin.required.text"),
                                   SettingsSyncBundle.message("settings.jba.plugin.required.title"))
        }
        return null
      }

      val defaultProvider = SettingsSyncCommunicatorProvider.PROVIDER_EP.extensionList.find { it.providerCode == DEFAULT_PROVIDER_CODE }
                            ?: return null
      DelegatingDefaultCommunicatorProvider.delegate = defaultProvider
      return defaultProvider.authService.login(parentComponent)
    }

    private fun getJars(basePath: Path): List<Path> {
      val jars = arrayListOf<Path>()
      basePath.toFile().walk().forEach {
        if (it.isFile && it.extension == "jar") {
          jars.add(it.toPath())
        }
      }
      return jars
    }

    override fun getUserData(userId: String): SettingsSyncUserData? = null
    override fun getAvailableUserAccounts(): List<SettingsSyncUserData> = emptyList()
  }

  private object DummyDefaultCommunicatorProvider : SettingsSyncCommunicatorProvider {
    override val providerCode: String
      get() = DEFAULT_PROVIDER_CODE
    override val authService = DummyAuthService

    override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator? {
      return null
    }
  }

  private object DelegatingDefaultCommunicatorProvider : SettingsSyncCommunicatorProvider {
    override val providerCode = DEFAULT_PROVIDER_CODE
    var delegate: SettingsSyncCommunicatorProvider = DummyDefaultCommunicatorProvider

    override val authService: SettingsSyncAuthService
      get() = delegate.authService

    override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator? {
      return delegate.createCommunicator(userId)
    }
  }
}