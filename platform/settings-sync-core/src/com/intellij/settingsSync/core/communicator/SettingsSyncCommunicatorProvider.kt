package com.intellij.settingsSync.core.communicator

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import org.jetbrains.annotations.ApiStatus

/**
 * This is an internal extension that requires an explicit license agreement with JetBrains s.r.o. for plugins.
 * Only IDE-bundled plugins are allowed to implement it.
 *
 * Contact https://platform.jetbrains.com/ for details.
 * You may not use this extension until it is unlocked in the platform for your plugin.
 */
@ApiStatus.Internal
@IntellijInternalApi
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
   * A pair contains:
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
   * Indicates whether a provider is available. Allows controlling provider availability inside the plugin
   */
  fun isAvailable(): Boolean = true
}

data class SettingsSyncUserData(
  val id: String,
  val providerCode: String,
  val name: String?,
  val email: String?,
  val printableName: String? = null,
)