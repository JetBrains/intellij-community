// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.ActivityTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
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
    @JvmStatic
    fun getInstance(): IdeUpdateWidgetState = service()

    @JvmStatic
    fun isEnabled(): Boolean = Registry.`is`("ide.update.toolbar.widget", false)

    /**
     * When shown, the toolbar button replaces the update item in the [com.intellij.ide.actions.SettingsEntryPointAction] menu.
     */
    @JvmStatic
    fun isWidgetShown(): Boolean = isEnabled() && getInstance().status.value != Status.NONE

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

  /**
   * Moves the button between the statuses.
   */
  fun updateStatus(value: Status) {
    if (!isEnabled()) {
      return
    }

    val current = mutableStatus.value
    // [Status.RESTART] is a final status
    if (current == value || current == Status.RESTART) {
      return
    }

    if (mutableStatus.compareAndSet(current, value)) {
      updateWidget()
    }
  }

  fun remindMeLater() {
    PropertiesComponent.getInstance().setValue(REMIND_LATER_TIME, System.currentTimeMillis().toString())
  }

  fun onDownloadFinished() {
    if (isEnabled() && mutableStatus.compareAndSet(Status.DOWNLOADING, Status.AVAILABLE)) {
      updateWidget()
    }
  }

  fun onRestartReady(command: Array<String>) {
    restartCommand = command
    updateStatus(Status.RESTART)
  }

  private fun updateWidget() {
    // repaint the toolbars, so the button appears, disappears or gets a new text without waiting for the next update session
    ActivityTracker.getInstance().inc()
  }
}
