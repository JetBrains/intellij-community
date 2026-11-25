package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.LookupManager
import com.intellij.driver.sdk.ui.components.common.editor.completionList
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CompletionActions(val driver: Driver, val ui: UiRobot) {
  fun invokeBasicCompletion() {
    driver.withContext {
      ideFrame {
        keyboard { escape() }
        invokeAction("CodeCompletion")

        waitForIndicators(15.seconds)
      }
    }
  }

  /**
   * Invokes basic completion for a prefix with a single match, and inserts the match.
   *
   * The IDE inserts a single match at once. In split mode the client shows the match in the pop-up, so Enter inserts it.
   */
  fun completeSingleMatch() {
    invokeBasicCompletion()
    if (!driver.isRemDevMode) return
    step("Insert the single match of the completion pop-up") {
      driver.withContext {
        ideFrame {
          completionList().waitFound(15.seconds)
          val items = completionList().items
          check(items.size == 1) { "The completion pop-up offers ${items.size} items instead of one: $items" }
          keyboard { enter() }
          completionList().waitNotFound(15.seconds)
        }
      }
    }
  }

  fun invokeSmartCompletion() {
    driver.withContext {
      ideFrame {
        keyboard { escape() }
        invokeAction("SmartTypeCompletion")

        waitForIndicators(15.seconds)
      }
    }
  }

  /**
   * The completion pop-up is shown and its items are calculated. A key typed during the calculation restarts the completion
   * with the typed prefix, so wait for this before typing into a pop-up that a template or an action opened.
   */
  fun waitForCalculatedItems() {
    step("Completion pop-up finishes the calculation") {
      driver.withContext {
        ideFrame { completionList().waitFound(15.seconds) }
        val project = singleProject()
        waitFor("Completion pop-up finishes the calculation", 15.seconds, 200.milliseconds) {
          !service(LookupManager::class, project).getInstance(project).getActiveLookup().isCalculating()
        }
      }
    }
  }

  /** The completion pop-up has an item containing every one of [expectedItems]. */
  fun shouldHaveItems(vararg expectedItems: String) {
    step("Completion pop-up has ${expectedItems.joinToString()}") {
      var currentItems = emptyList<String>()
      driver.withContext {
        ideFrame {
          completionList().should(
            message = "Completion pop-up has ${expectedItems.joinToString()}",
            errorMessage = { "Pop-up shows: $currentItems" },
          ) {
            currentItems = items.map { it.packed() }
            expectedItems.all { expected -> currentItems.any { it.contains(expected.packed()) } }
          }
        }
      }
    }
  }

  /** The completion pop-up has no item containing any of [unexpectedItems]. */
  fun shouldNotHaveItems(vararg unexpectedItems: String) {
    step("Completion pop-up has none of ${unexpectedItems.joinToString()}") {
      var currentItems = emptyList<String>()
      driver.withContext {
        ideFrame {
          completionList().should(
            message = "Completion pop-up has none of ${unexpectedItems.joinToString()}",
            errorMessage = { "Pop-up shows: $currentItems" },
          ) {
            currentItems = items.map { it.packed() }
            currentItems.isNotEmpty() && unexpectedItems.none { unexpected -> currentItems.any { it.contains(unexpected.packed()) } }
          }
        }
      }
    }
  }

  /**
   * An item is read as the texts its renderer paints, joined with a space, and the name itself is painted as two
   * fragments split at the end of the matched prefix: `Print` of `fmt.Prin` arrives as `(a ...any) Prin t`.
   * Spaces are dropped so that a name can be matched as a whole.
   */
  private fun String.packed(): String = replace(" ", "")
}
