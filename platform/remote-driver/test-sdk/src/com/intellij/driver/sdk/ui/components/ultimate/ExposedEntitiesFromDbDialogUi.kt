package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.waitFor
import javax.swing.JCheckBox
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun IdeaFrameUI.exposedEntitiesFromDbDialog(action: ExposedEntitiesFromDbDialogUi.() -> Unit = {}): ExposedEntitiesFromDbDialogUi =
  x(ExposedEntitiesFromDbDialogUi::class.java) { byTitle("Exposed Entities from DB") }.apply(action)

class ExposedEntitiesFromDbDialogUi(data: ComponentData) : DialogUiComponent(data) {

  val refreshDataSourceButton: UiComponent get() = x("//div[@accessiblename='Refresh IDEA Data Source']")

  fun refreshDataSource() = refreshDataSourceButton.click()

  fun waitForTable(tableName: String, timeout: Duration = 2.minutes) {
    waitFor("Table '$tableName' to appear in entity tree", timeout) {
      x("//div[@class='ExEntityRelationTree'][contains(@visible_text, '$tableName')]").present()
    }
  }

  fun setCheckboxes(vararg labels: String) {
    labels.forEach { label ->
      checkBox { and(byType(JCheckBox::class.java), byAccessibleName(label)) }.check()
    }
  }
}
