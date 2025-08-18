package com.intellij.settingsSync.core.auth

import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.Icon

/**
 * This is an internal extension that requires an explicit license agreement with JetBrains s.r.o. for plugins.
 * Only IDE-bundled plugins are allowed to implement it.
 *
 * Contact https://platform.jetbrains.com/ for details.
 * You may not use this extension until it is unlocked in the platform for your plugin.
 */
@ApiStatus.Internal
interface SettingsSyncAuthService {
  /**
   * short, self-explanatory and unique code name of the provider. May or may not match the provider code.
   * @see com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider#getProviderCode()
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
   * If the function is null, a logout link in the UI is not visible
   */
  val logoutFunction: (suspend (Component?) -> Unit)?
    get() = null

  /**
   * Starts the login procedure (if necessary) and returns the Deferred of the logged-in user
   */
  suspend fun login(parentComponent: Component?): SettingsSyncUserData?

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

  fun getPendingUserAction(userId: String): PendingUserAction? = null

  /**
   * @param message - text message that will be shown in the configurable label
   * @param actionTitle - text to use in the button
   * @param actionDescription - test that will be used in the explanation, i.e. click "[actionTitle]" to "[actionDescription]".
   * If that parameter is null [message] will be used instead.
   * @param action - action to perform when clicked the button. The action will be performed under EDT
   */
  data class PendingUserAction(
    val message: @Nls String,
    val actionTitle: @Nls String,
    val actionDescription: @Nls String? = null,
    val action: suspend (Component?) -> Unit,
  )
}