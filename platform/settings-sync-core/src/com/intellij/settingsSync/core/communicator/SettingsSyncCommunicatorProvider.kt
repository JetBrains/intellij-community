package com.intellij.settingsSync.core.communicator

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService

interface SettingsSyncCommunicatorProvider {

  /**
   * a unique code which identifies the provider (for example, "jba")
   */
  val providerCode: String

  /**
   * Authentication service. Is used during the login process as well as for storing the file
   */
  val authService: SettingsSyncAuthService

  /**
   * Used in the main configurable after Sync UI, Code, ...
   * a Pair:
   * * link text, for instance: Learn more
   * * actual link itself, for instance: https://www.jetbrains.com/help/idea/sharing-your-ide-settings.html
   */
  val learnMoreLinkPair: Pair<String, String>?
    get() = null

  /**
   * Used in the select provider dialog.
   * a Pair:
   * * link text, for instance: Learn more
   * * actual link itself, for instance: https://www.jetbrains.com/help/idea/sharing-your-ide-settings.html
   */
  val learnMoreLinkPair2: Pair<String, String>?
    get() = null

  /**
   * Creates a communicator (using the login data from authService)
   */
  fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator?

  /**
   * Indicates whether provider is available. Allows to control provider availability inside the plugin
   */
  fun isAvailable(): Boolean = true

  companion object {
    @JvmField
    val PROVIDER_EP = ExtensionPointName.create<SettingsSyncCommunicatorProvider>("com.intellij.settingsSync.communicatorProvider")
  }
}

data class SettingsSyncUserData(
  val id: String,
  val providerCode: String,
  val name: String?,
  val email: String?,
  val printableName: String? = null
)