package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.withThreadDumps
import com.intellij.openapi.util.SystemInfo
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.seconds

class NotebookTableOutputUi(data: ComponentData) : UiComponent(data) {

  private val pager
    get() = x("//div[@class='ActionButtonWithText' and contains(@myaction, 'Change Page Size')]", JButtonUiComponent::class.java)

  private val downloadButton
    get() = x("//div[@myicon='download.svg']", JButtonUiComponent::class.java)

  val openInNewTabButton: JButtonUiComponent
    get() = x("//div[@class='ActionButton' and contains(@myaction, 'Open In New Tab')]", JButtonUiComponent::class.java)

  val isStatic: JButtonUiComponent
    get() = x("//div[@class='ActionButtonWithText' and contains(@visible_text, 'Static Output')]", JButtonUiComponent::class.java)

  val tableDimension: JLabelUiComponent
    get() = x("//div[@class='MyLabel' and contains(@text, 'rows × ')]", JLabelUiComponent::class.java)


  val tableBreadcrumbs: JTextFieldUI
    get() = x("//div[@class='Breadcrumbs']", JTextFieldUI::class.java)


  val header: UiComponent
    get() = x(xQuery { byClass("MyTableHeader") })

  val tableView: JTableUiComponent
    get() = x("//div[@class='TableResultView']", JTableUiComponent::class.java)

  fun goTopLevel(): Unit = tableBreadcrumbs.getAllTexts().first().click()

  fun changePageSizeTo(n: Int) {
    if (tableView.rowCount() == n) return

    step("Waiting for a page and pager to load") {
      waitFor(timeout = 15.seconds) {
        val currentPagerText = pager.getAllTexts().first().text
        val rowCount = tableView.rowCount()
        "$rowCount rows" in currentPagerText
      }
    }

    pager.click()
    driver.ideFrame {
      popup().waitOneText { it.text.contains("Custom") }.click()

      dialog {
        keyboard {
          driver.ui.pasteText(n.toString())
        }
        okButton.click()
      }
    }

    driver.withThreadDumps(
      folderName = "threadDumps-changePageSize",
      fileNamePrefix = "threadDump",
      interval = 5.seconds
    ) {
      waitFor("expect the pager text to change", 30.seconds) {
        tableView.rowCount() == n
      }
    }
  }

  private enum class NavigationDirection {
    FORWARD, BACKWARD
  }
  fun goNextPage(): Unit = goOtherPage(NavigationDirection.FORWARD)
  fun goPreviousPage(): Unit = goOtherPage(NavigationDirection.BACKWARD)

  private fun goOtherPage(direction: NavigationDirection) {
    val iconFileName = when (direction) {
      NavigationDirection.FORWARD -> "playForward.svg"
      NavigationDirection.BACKWARD -> "playBack.svg"
    }
    val button = x("//div[@myicon='$iconFileName']")
    val textBefore = tableView.getValueAt(0, 0)
    driver.withThreadDumps(
      "threadDumps-goOtherPage",
      "threadDump-waiting-$iconFileName",
      5.seconds,
    ) {
      button.waitFound(30.seconds)
    }
    button.click()
    waitFor("expect the cell [0,0] doesn't contain '$textBefore' anymore") {
      tableView.getValueAt(0, 0) != textBefore
    }
  }

  fun clickHeaderContextCommand(headerTitle: String, command: String) {
    header.getAllTexts().firstOrNull {
      it.text == headerTitle
    }?.run {
      rightClick()
      driver.ideFrame {
        popup().waitOneText { it.text.contains(command) }.click()
      }
    } ?: error("can't find the header title '$headerTitle'")
  }

  fun exportTo(fileName: String) {
    downloadButton.click()
    driver.ideFrame {
      dialog().apply {
        checkBox(xpath = "//div[@class='JBCheckBox' and contains(@text,'Add column header')]").check()
        textField(xpath = "//div[contains(@notifyaction, 'notify-field-accept')]").click()
        keyboard {
          hotKey(if (SystemInfo.isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL, KeyEvent.VK_A)
          driver.ui.pasteText(fileName)
        }
        button("Export to File").click()
      }
    }
  }
}
