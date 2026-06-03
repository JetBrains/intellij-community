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

  fun isConnectionSuccessful(driverNameContains: String): Boolean =
    x("//div[@class='GrayLabel'][contains(@visible_text, '$driverNameContains')]").present()

  fun downloadDriverFilesIfPrompted(): Boolean {
    if (!downloadDriverFilesButton.present()) return false
    downloadDriverFilesButton.click()
    return true
  }

  fun waitForConnectionResultOrDriverPrompt(driverNameContains: String, timeout: Duration = 30.seconds) {
    waitFor("Test Connection result or driver download prompt", timeout) {
      isConnectionSuccessful(driverNameContains) || downloadDriverFilesButton.present()
    }
  }

  fun waitForSuccessfulConnection(driverNameContains: String, timeout: Duration = 2.minutes) {
    waitFor("Connection test succeeded for $driverNameContains", timeout) {
      isConnectionSuccessful(driverNameContains)
    }
  }
}
