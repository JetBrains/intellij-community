package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.mainToolbar
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.withRetries
import java.awt.Point
import javax.swing.JList

fun IdeaFrameUI.runConfigurationsPopup(f: PopupUiComponent.() -> Unit = {}) {
  withRetries("Single popup is present", 2) {
    mainToolbar.runWidget.click()
    popup().waitFound()
  }.apply(f)
}

fun PopupUiComponent.runConfigurationsList(locator: String = xQuery { byType(JList::class.java) }, f: RunConfigurationsPopupList.() -> Unit = {}) =
  x(locator, RunConfigurationsPopupList::class.java).apply(f)

class RunConfigurationsPopupList(data: ComponentData) : JListUiComponent(data) {
  fun clickRun(itemText: String, exactMatch: Boolean = true) {
    clickItemWithHorizontalOffsetFromTheEnd(itemText, exactMatch, RUN_BUTTON_HORIZONTAL_OFFSET)
  }

  fun clickRunAtIndex(index: Int) {
    clickItemAtIndexWithHorizontalOffsetFromTheEnd(index, RUN_BUTTON_HORIZONTAL_OFFSET)
  }

  fun clickRerun(itemText: String, exactMatch: Boolean = true) {
    val itemIndex = checkNotNull(findItemIndex(itemText, exactMatch)) { "$itemText not found" }
    var rerunButtonOffset = RERUN_BUTTON_HORIZONTAL_OFFSET
    if (collectIconsAtIndex(itemIndex).none { it.contains(ICON_DEBUG) }) {
      rerunButtonOffset -= ICON_WIDTH
    }
    clickItemAtIndexWithHorizontalOffsetFromTheEnd(itemIndex, rerunButtonOffset)
  }

  fun clickDebug(itemText: String, exactMatch: Boolean = true) {
    clickItemWithHorizontalOffsetFromTheEnd(itemText, exactMatch, DEBUG_BUTTON_HORIZONTAL_OFFSET)
  }

  fun clickMoreActions(itemText: String, exactMatch: Boolean = true) {
    clickItemWithHorizontalOffsetFromTheEnd(itemText, exactMatch, MORE_BUTTON_HORIZONTAL_OFFSET)
  }

  private fun clickItemWithHorizontalOffsetFromTheEnd(itemText: String, exactMatch: Boolean, offset: Int) {
    val idx = checkNotNull(findItemIndex(itemText, exactMatch)) { "$itemText not found" }
    clickItemAtIndexWithHorizontalOffsetFromTheEnd(idx, offset)
  }

  private fun clickItemAtIndexWithHorizontalOffsetFromTheEnd(index: Int, offset: Int) {
    val cellBounds = driver.withContext(OnDispatcher.EDT) { listComponent.getCellBounds(index, index) }
    robot.click(component, Point(cellBounds.x + cellBounds.width - offset, cellBounds.y + cellBounds.height / 2))
  }

  companion object {
    private const val ICON_WIDTH = 26
    private const val MORE_BUTTON_HORIZONTAL_OFFSET = ICON_WIDTH
    private const val DEBUG_BUTTON_HORIZONTAL_OFFSET = MORE_BUTTON_HORIZONTAL_OFFSET + ICON_WIDTH
    private const val RUN_BUTTON_HORIZONTAL_OFFSET = DEBUG_BUTTON_HORIZONTAL_OFFSET + ICON_WIDTH
    private const val STOP_BUTTON_HORIZONTAL_OFFSET = DEBUG_BUTTON_HORIZONTAL_OFFSET + ICON_WIDTH
    private const val RERUN_BUTTON_HORIZONTAL_OFFSET = STOP_BUTTON_HORIZONTAL_OFFSET + ICON_WIDTH

    const val ICON_DEBUG = "debug.svg"
    const val ICON_RUN = "run.svg"
    const val ICON_MORE = "moreVertical.svg"
    const val ICON_STOP = "stop.svg"
  }
}