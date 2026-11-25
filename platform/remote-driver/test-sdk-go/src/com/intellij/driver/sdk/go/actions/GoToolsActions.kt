package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.go.ui.goToolsMenu
import com.intellij.driver.sdk.go.ui.goToolsMenuItems
import com.intellij.driver.sdk.go.ui.rightClickEditorOn
import com.intellij.driver.sdk.go.ui.shouldHaveRunOutput
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The `Go Tools` actions, which hand the file of the opened editor over to a Go command line tool.
 *
 * They are invoked from the context menu of the editor — the same `GoTools` group the `Tools` menu shows, and the one
 * route the robot can take on every operating system.
 *
 * Every action runs its tool in the background and reports nothing when the tool succeeds, so the caller waits for
 * what the tool did — the rewritten file for `go fmt`, the console of [checkToolOutput] for `go vet`.
 */
class GoToolsActions(val driver: Driver, val ui: UiRobot) {

  fun runGoFmtFile(anchor: String = PACKAGE_CLAUSE) {
    invokeGoToolsAction(GO_FMT_FILE, anchor)
  }

  fun runGoVetFile(anchor: String = PACKAGE_CLAUSE) {
    invokeGoToolsAction(GO_VET_FILE, anchor)
  }

  /**
   * Waits for [texts] in a console of the Run tool window, where a Go tool reports the problems it has found.
   *
   * The tool window is opened here: `GoExecutor` leaves it closed whenever another tool window is active, and a
   * closed tool window holds no console to read.
   */
  fun checkToolOutput(vararg texts: String, timeout: Duration = OUTPUT_TIMEOUT) {
    step("Verify the tool output reports ${texts.joinToString(prefix = "[", postfix = "]")}") {
      driver.withContext {
        ui.ideFrame {
          // The Run tool window is registered together with the console of the tool, hence the same timeout.
          leftToolWindowToolbar.runButton.waitFound(timeout).open()
          shouldHaveRunOutput(*texts, timeout = timeout)
        }
      }
    }
  }

  /**
   * Picks [item] in the `Go Tools` submenu of the context menu the editor shows on a right click on [anchor].
   *
   * The menu windows live outside the IDE frame, hence the search from the root of [ui].
   */
  private fun invokeGoToolsAction(item: String, anchor: String) {
    step("Invoke '$GO_TOOLS_MENU | $item' from the context menu of the editor") {
      driver.withContext {
        ui.rightClickEditorOn(anchor)
        ui.goToolsMenu()
          .shouldBe("The context menu of the editor has no '$GO_TOOLS_MENU' submenu", present, POPUP_TIMEOUT)
          .click()
        val items = ui.goToolsMenuItems(item)
        waitFor(
          message = "'$item' is present in the '$GO_TOOLS_MENU' submenu",
          timeout = POPUP_TIMEOUT,
          errorMessage = { "'$item' is not present in the '$GO_TOOLS_MENU' submenu" },
        ) { items.list().isNotEmpty() }
        items.list().first().click()
      }
    }
  }

  private companion object {
    const val GO_TOOLS_MENU = "Go Tools"
    const val GO_FMT_FILE = "Go Fmt File"
    const val GO_VET_FILE = "Go Vet File"

    const val PACKAGE_CLAUSE = "package "

    val POPUP_TIMEOUT = 30.seconds

    val OUTPUT_TIMEOUT = 10.minutes
  }
}
