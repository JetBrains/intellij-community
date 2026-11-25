package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.popups.DocumentationHintEditorPaneUi
import com.intellij.driver.sdk.ui.notPresent
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Quick Documentation flows: the popup opened by the `QuickJavaDoc` action (Ctrl+Q on Windows/Linux,
 * F1 on macOS) and the one the editor shows on mouse hover. Both are rendered by the same
 * `DocumentationHintEditorPane`, so their content is read the same way.
 */
class DocumentationActions(val driver: Driver, val ui: UiRobot) {

  /**
   * A fresh component per call on purpose: [DocumentationHintEditorPaneUi] casts the pane once and keeps
   * the reference, and such a reference only stays alive within the `Driver.withContext { }` it was
   * created in. Reusing one instance for the next popup fails with an expired weak reference.
   */
  private fun documentationPopup() = ui.x(DocumentationHintEditorPaneUi::class.java) {
    byClass("DocumentationHintEditorPane")
  }

  /** Moves the caret to [line]:[column] and opens the Quick Documentation popup. */
  fun showQuickDocumentation(line: Int, column: Int) {
    step("Invoke Quick Documentation at $line:$column") {
      driver.withContext {
        ideFrame {
          codeEditor { goToPosition(line, column) }
        }
      }
      driver.invokeAction("QuickJavaDoc", now = false)
    }
  }

  /** Moves the mouse over [line]:[column] so that the editor shows the documentation popup on hover. */
  fun hoverForDocumentation(line: Int, column: Int) {
    step("Hover over the symbol at $line:$column") {
      driver.withContext {
        ideFrame {
          codeEditor { hoverOnPosition(line, column) }
        }
      }
    }
  }

  /** Wait for the documentation popup and assert its content contains every [expectedFragments] entry. */
  fun verifyDocumentationContains(vararg expectedFragments: String, timeout: Duration = DOCUMENTATION_TIMEOUT) {
    step("Verify documentation contains: ${expectedFragments.joinToString()}") {
      var documentation = ""
      driver.withContext {
        documentationPopup().should(
          message = "Documentation contains ${expectedFragments.joinToString()}",
          timeout = timeout,
          errorMessage = { "Documentation shows: $documentation" },
        ) {
          documentation = getText().asRenderedText()
          expectedFragments.all { documentation.contains(it) }
        }
      }
    }
  }

  fun closeDocumentation() {
    step("Close the documentation popup") {
      driver.withContext {
        ideFrame {
          codeEditor { clickOnPosition(FILE_START_LINE, FILE_START_COLUMN) }
        }
      }
      driver.withContext {
        documentationPopup().shouldBe("Documentation popup is still shown", notPresent)
      }
    }
  }

  /**
   * The popup holds HTML in which Go types are rendered as navigation links, so the markup is dropped
   * before matching: `B → <a href="psi_element://...">Foo</a>` becomes `B → Foo`. Whitespace is
   * collapsed as well, because the pane wraps long lines of the markup it serializes.
   */
  private fun String.asRenderedText(): String =
    replace(HTML_TAG, " ")
      .replace("&nbsp;", " ")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")
      .replace(WHITESPACE, " ")
      .trim()

  companion object {
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    private val DOCUMENTATION_TIMEOUT = 30.seconds

    /**
     * Where [closeDocumentation] presses the mouse: the popup is anchored under the symbol it documents,
     * so the first character of the file stays outside of it.
     */
    private const val FILE_START_LINE = 1
    private const val FILE_START_COLUMN = 1
  }
}
