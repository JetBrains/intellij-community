// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.ActivityTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit

private const val REMIND_LATER_TIME = "IdeUpdateWidget.RemindLaterTime"
private val REMIND_LATER_TIMEOUT_MS = TimeUnit.DAYS.toMillis(3)

@ApiStatus.Internal
@Service(Service.Level.APP)
class IdeUpdateWidgetState {

  enum class Status {
    /**
     * There is no update, or it is an EAP/nightly one which stays in the Settings menu
     */
    NONE,

    /**
     * A release channel update is available
     */
    AVAILABLE,

    /**
     * The announced update is being downloaded
     */
    DOWNLOADING,

    /**
     * The announced update has been downloaded
     */
    RESTART,
  }

  companion object {

    private val LOG = logger<IdeUpdateWidgetState>()

    @JvmStatic
    fun getInstance(): IdeUpdateWidgetState = service()

    @JvmStatic
    fun isEnabled(): Boolean = Registry.`is`("ide.update.toolbar.widget", true)

    /**
     * When the toolbar button owns the update, it replaces the item in the [com.intellij.ide.actions.SettingsEntryPointAction] menu.
     */
    @JvmStatic
    fun isUpdateAvailable(): Boolean = isEnabled() && getInstance().status.value != Status.NONE

    /**
     * Whether the user sees the button.
     */
    @JvmStatic
    fun isWidgetShown(): Boolean = isUpdateAvailable() && WelcomeFrame.getInstance() == null

    /**
     * Whether an update must not be announced, because the user has asked to be reminded later.
     */
    @JvmStatic
    fun isRemindLaterActive(): Boolean {
      if (!isEnabled()) {
        return false
      }

      val elapsed = System.currentTimeMillis() - PropertiesComponent.getInstance().getLong(REMIND_LATER_TIME, 0)
      return elapsed < REMIND_LATER_TIMEOUT_MS
    }
  }

  private val mutableStatus = MutableStateFlow(Status.NONE)

  val status: StateFlow<Status> = mutableStatus.asStateFlow()

  @Volatile
  var restartCommand: Array<String>? = null
    private set

  fun onUpdateFound(found: Boolean) {
    if (!isEnabled()) {
      return
    }

    LOG.info("onUpdateFound: $found")

    updateStatus(if (found) Status.AVAILABLE else Status.NONE)
  }

  fun onDownloadStarted() {
    if (!isUpdateAvailable()) {
      return
    }

    LOG.info("onDownloadStarted")

    updateStatus(Status.DOWNLOADING)
  }

  /**
   * Returns the button from [Status.DOWNLOADING] when the patch task ends without a patch.
   *
   * A prepared patch has already left [Status.DOWNLOADING] for [Status.RESTART], so only a failed download and a cancellation
   * reach the transition.
   */
  fun onDownloadFinished() {
    if (!isUpdateAvailable()) {
      return
    }

    LOG.info("onDownloadFinished")

    updateStatus(Status.AVAILABLE)
  }

  /**
   * The [command] is `null` when restart is not available
   */
  fun onRestartReady(command: Array<String>?) {
    if (!isUpdateAvailable()) {
      return
    }

    LOG.info("onRestartReady, restart capable: ${command != null}")

    restartCommand = command
    updateStatus(Status.RESTART)
  }

  fun remindMeLater() {
    if (!isEnabled()) {
      return
    }

    LOG.info("remindMeLater")

    PropertiesComponent.getInstance().setValue(REMIND_LATER_TIME, System.currentTimeMillis().toString())
  }

  fun isClickable(): Boolean = when (status.value) {
    Status.DOWNLOADING -> false
    Status.RESTART -> restartCommand != null
    else -> true
  }

  /**
   * Moves the button between the statuses. Every caller checks first that the button accepts the transition.
   */
  private fun updateStatus(value: Status) {
    val current = mutableStatus.value
    if (current == value) {
      return
    }

    // [Status.RESTART] is a final status
    if (current == Status.RESTART) {
      LOG.info("Status $value is declined, $current is final")

      return
    }

    if (mutableStatus.compareAndSet(current, value)) {
      LOG.info("Status changed: $current -> $value")

      updateWidget()
    }
  }

  private fun updateWidget() {
    // repaint the toolbars, so the button appears, disappears or gets a new text without waiting for the next update session
    ActivityTracker.getInstance().inc()
  }
}
