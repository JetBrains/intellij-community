package com.intellij.settingsSync.core.communicator

import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.util.resettableLazy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RemoteCommunicatorHolder : SettingsSyncEventListener {
  private val logger = logger<RemoteCommunicatorHolder>()
  const val DEFAULT_PROVIDER_CODE = "jba"
  const val DEFAULT_USER_ID = "jba"

  // pair userId:remoteCommunicator
  private val communicatorLazy = resettableLazy {
    createRemoteCommunicator()
  }

  fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator? = communicatorLazy.value ?: createRemoteCommunicator()

  fun getAuthService() = getCurrentProvider()?.authService

  fun isAvailable() = communicatorLazy.value != null

  fun invalidateCommunicator() = communicatorLazy.reset().also {
    logger.warn("Invalidating remote communicator")
  }

  fun getCurrentUserData() : SettingsSyncUserData? {
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
  }

  fun createRemoteCommunicator(provider: SettingsSyncCommunicatorProvider, userId: String): SettingsSyncRemoteCommunicator? {
    return provider.createCommunicator(userId)
  }

  private fun createRemoteCommunicator(): SettingsSyncRemoteCommunicator? {
    val provider: SettingsSyncCommunicatorProvider = getCurrentProvider() ?: run {
      logger.warn("Attempting to create remote communicator without active provider")
      return null
    }
    val userId = SettingsSyncLocalSettings.getInstance().userId ?: run {
      logger.warn("Empty current userId. Communicator will not be created.")
      return null
    }

    val currentUserData = provider.authService.getUserData(userId) ?: run {
      logger.warn("Empty current user data. Communicator will not be created.")
      return null
    }
    val communicator: SettingsSyncRemoteCommunicator = provider.createCommunicator(currentUserData.id) ?: run {
      logger.warn("Provider '${provider.providerCode}' returned empty communicator")
      return null
    }
    return communicator
  }

  fun getAvailableProviders(): List<SettingsSyncCommunicatorProvider> {
    val extensionList = SettingsSyncCommunicatorProvider.PROVIDER_EP.extensionList
    return extensionList
  }

  fun getDefaultProvider(): SettingsSyncCommunicatorProvider? {
    return getProvider(DEFAULT_PROVIDER_CODE)!!
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
}