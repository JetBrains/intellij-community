// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.ActivityTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val REMIND_LATER_TIME = "IdeUpdateWidget.RemindLaterTime"
private val REMIND_LATER_TIMEOUT = 3.days
private val WIDGET_SHOW_TIMEOUT = 3.seconds

@ApiStatus.Internal
@Service(Service.Level.APP)
class IdeUpdateWidgetState(private val coroutineScope: CoroutineScope) {

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
     * The button is not shown when the main toolbar is hidden, for example, in the distraction-free mode.
     */
    @JvmStatic
    fun isWidgetShown(): Boolean = isUpdateAvailable() && getInstance().shownWidgets.value > 0

    /**
     * Whether an update must not be announced, because the user has asked to be reminded later.
     */
    @JvmStatic
    fun isRemindLaterActive(): Boolean {
      if (!isEnabled()) {
        return false
      }

      val elapsed = System.currentTimeMillis() - PropertiesComponent.getInstance().getLong(REMIND_LATER_TIME, 0)
      return elapsed.milliseconds < REMIND_LATER_TIMEOUT
    }
  }

  private val mutableStatus = MutableStateFlow(Status.NONE)

  /**
   * The number of the buttons that are on the screen now.
   */
  private val shownWidgets = MutableStateFlow(0)

  val status: StateFlow<Status> = mutableStatus.asStateFlow()

  @Volatile
  var restartAction: Runnable? = null
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
   * The widget runs [action] on a click. The [action] is `null` when restart is not available
   */
  fun onRestartReady(action: Runnable?) {
    if (!isUpdateAvailable()) {
      return
    }

    LOG.info("onRestartReady, restart capable: ${action != null}")

    restartAction = action
    updateStatus(Status.RESTART)
  }

  fun remindMeLater() {
    if (!isEnabled()) {
      return
    }

    LOG.info("remindMeLater")

    PropertiesComponent.getInstance().setValue(REMIND_LATER_TIME, System.currentTimeMillis().toString())
  }

  /**
   * The button runs [block] while it is on the screen, so [isWidgetShown] knows that the user sees the button.
   */
  suspend fun whileWidgetShown(block: suspend () -> Unit) {
    shownWidgets.update { it + 1 }
    try {
      block()
    }
    finally {
      shownWidgets.update { it - 1 }
    }
  }

  /**
   * Runs [action] on the EDT when the user does not see the button.
   * The toolbar adds the button some time after a status change, so the check waits for [WIDGET_SHOW_TIMEOUT].
   */
  fun runIfWidgetNotShown(action: () -> Unit) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val shown = isUpdateAvailable() && withTimeoutOrNull(WIDGET_SHOW_TIMEOUT) { shownWidgets.first { it > 0 } } != null
      if (!shown) {
        action()
      }
    }
  }

  fun isClickable(): Boolean = when (status.value) {
    Status.DOWNLOADING -> false
    Status.RESTART -> restartAction != null
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
