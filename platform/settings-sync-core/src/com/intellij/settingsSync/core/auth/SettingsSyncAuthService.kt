package com.intellij.settingsSync.core.auth

import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import java.awt.Component
import javax.swing.Icon

interface SettingsSyncAuthService {
  /**
   * short, self-explanatory and unique code name of the provider. May or may not match the
   * @see com.intellij.settingsSync.communicator.SettingsSyncCommunicatorProvider#getProviderCode()
   */
  val providerCode: String

  /**
   * Free-form name of the provider
   */
  val providerName: String

  /**
   * provider icon
   */
  val icon: Icon?

  /**
   * Provides a function/action responsible for the logout procedure or navigates a user to the place where they can log out themselves.
   * The method must call `SettingsSyncEvents.getInstance().fireLoginStateChanged()` in order to propagate the changed state.
   * If function is null, logout link in the UI is not visible
   */
  val logoutFunction: ( suspend (Component?) -> Unit)?
    get() = null

  /**
   * Starts the login procedure (if necessary) and returns the Deferred of the logged-in user
   */
  suspend fun login(parentComponent: Component?) : SettingsSyncUserData?

  /**
   * Data of the current user. If there's no user, return null
   * This data is used for in the local git repo as well as UI (if necessary)
   */
  fun getUserData(userId: String): SettingsSyncUserData?

  fun getAvailableUserAccounts(): List<SettingsSyncUserData>

  /**
   * Indicates whether the current provider supports cross-IDE sync
   */
  fun crossSyncSupported(): Boolean = true
}