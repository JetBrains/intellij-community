package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

fun IdeaFrameUI.dataSourcesAndDriversDialog(action: DataSourcesAndDriversDialogUi.() -> Unit = {}): DataSourcesAndDriversDialogUi =
  x(DataSourcesAndDriversDialogUi::class.java) { byTitle("Data Sources and Drivers") }.apply(action)

class DataSourcesAndDriversDialogUi(data: ComponentData) : DialogUiComponent(data) {

  val testConnectionButton: UiComponent = x("//div[@accessiblename='Test Connection']")
  val downloadDriverFilesButton: UiComponent = x("//div[@accessiblename='Download Driver Files']")

  fun testConnection() = testConnectionButton.click()

  fun isConnectionSuccessful(): Boolean = hasSubtext("Succeeded")

  fun waitForSuccessfulConnection(timeout: Duration = 3.minutes) {
    waitFor("Test Connection succeeded", timeout) { isConnectionSuccessful() }
  }

  fun downloadDriverFilesIfPrompted(
    promptTimeout: Duration = 30.seconds,
    downloadTimeout: Duration = 3.minutes,
  ): Boolean {
    val prompted = runCatching {
      waitFor("'Download Driver Files' prompt to appear", promptTimeout) { downloadDriverFilesButton.present() }
    }.isSuccess
    if (!prompted) return false
    downloadDriverFilesButton.click()
    downloadDriverFilesButton.waitNotFound(downloadTimeout)
    return true
  }
}
