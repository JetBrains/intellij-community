package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.shouldNotContainText
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForCodeAnalysis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class EditorActions(val driver: Driver, val ui: UiRobot) {

  /** The line of the first occurrence of [anchor] in the editor of [fileName]. The first line is 1. */
  fun lineOf(fileName: String, anchor: String): Int = driver.withContext {
    ui.ideFrame().codeEditorForFile(fileName).run {
      require(anchor in text) { "'$anchor' is not found in '$fileName'" }
      getLineNumber(anchor)
    }
  }

  /**
   * Waits until the text of the opened editor contains every one of [fragments].
   *
   * The text is polled rather than read once: a tool that rewrites the file outside the IDE (`go fmt`) reaches the
   * document only once the change has been picked up from the file system.
   */
  fun assertTextContains(vararg fragments: String, timeout: Duration = 2.minutes) {
    require(fragments.isNotEmpty()) { "fragments must not be empty" }
    step("Verify the editor text contains ${fragments.joinToString(prefix = "[", postfix = "]")}") {
      driver.withContext {
        ui.ideFrame {
          codeEditor {
            var actual = ""
            var missing = fragments.toList()
            waitFor(
              message = "Editor text contains ${fragments.joinToString(prefix = "[", postfix = "]")}",
              timeout = timeout,
              errorMessage = { "Editor text is missing $missing. Actual text:\n$actual" },
            ) {
              actual = text
              missing = fragments.filterNot { it in actual }
              missing.isEmpty()
            }
          }
        }
      }
    }
  }

  /** Closes every open editor, except the editors of the files which [keep] accepts. */
  fun closeAllEditors(keep: (VirtualFile) -> Boolean = { false }) {
    step("Close the open editors") {
      driver.withContext(OnDispatcher.EDT) {
        val editorManager = service<FileEditorManager>(singleProject())
        editorManager.getAllEditors()
          .map { it.getFile() }
          .distinctBy { it.getPath() }
          .filterNot(keep)
          .forEach { editorManager.closeFile(it) }
      }
    }
  }

  fun assertTextDoesNotContain(fragment: String, timeout: Duration = 30.seconds) {
    step("Verify the editor text does not contain '$fragment'") {
      driver.withContext {
        ui.ideFrame {
          codeEditor().shouldNotContainText(fragment, timeout = timeout)
        }
      }
    }
  }

  fun typeBefore(anchor: String, text: String) {
    step("Type '$text' in front of '$anchor' in the editor") {
      driver.withContext {
        ui.ideFrame {
          codeEditor {
            clickOn(anchor)
            moveCaretToText(anchor)
          }
          keyboard { typeText(text) }
        }
      }
    }
  }

  /**
   * Waits until Code Vision shows [expectedHints] — the hint expected next to a declaration, keyed by the text that
   * declaration starts with (`"func walk(" to "1 usage"`). A line may carry more hints than the expected one.
   */
  fun assertCodeVisionHints(expectedHints: Map<String, String>, timeout: Duration = 30.seconds) {
    require(expectedHints.isNotEmpty()) { "expectedHints must not be empty" }
    step("Expand all the foldings of the editor") {
      driver.withContext {
        ui.ideFrame {
          codeEditor { expandAllFoldings() }
        }
      }
    }
    expectedHints.forEach { (declaration, expectedHint) ->
      step("Verify Code Vision shows '$expectedHint' next to '$declaration'") {
        driver.withContext {
          ui.ideFrame {
            codeEditor {
              require(declaration in text) { "'$declaration' is not found in the file the editor shows" }
              val line = getLineNumber(declaration)
              moveCaretToText(declaration)
              var hints = emptyList<String>()
              waitFor(
                message = "Code Vision shows '$expectedHint' next to '$declaration' of line $line",
                timeout = timeout,
                errorMessage = { "Line $line shows the hints $hints" },
              ) {
                scrollToCaret()
                hints = codeVisionHintsOnLine(line)
                expectedHint in hints
              }
            }
          }
        }
      }
    }
  }

  /**
   * The texts painted past the code of [line] — the Code Vision hints of the declaration the line holds.
   *
   * Both coordinates are in the space of the editor component: the text extractor reports the point of a text relative
   * to it, and [Editor.offsetToXY] answers in the same space. So the hints of a line are the texts painted within the
   * height of the line and to the right of the end of its code.
   */
  private fun JEditorUiComponent.codeVisionHintsOnLine(line: Int): List<String> {
    val (lineEnd, lineHeight) = interact { offsetToXY(getDocument().getLineEndOffset(line - 1)) to getLineHeight() }
    return getAllTexts()
      .filter { it.point.x > lineEnd.x && it.point.y >= lineEnd.y && it.point.y < lineEnd.y + lineHeight }
      .map { it.text }
  }

  /**
   * Waits until there are no error highlights in the file.
   *
   * A single check is not enough: the daemon may complete a pass while the project model is still catching up
   * (right after a branch checkout Go module dependencies are re-resolved), so unresolved references are reported
   * for a while and disappear on one of the next passes. Highlights are re-read after every completed pass,
   * so the file must stay red for the whole [timeout] to fail the check.
   */
  fun assertNoErrorHighlights(editor: Editor, timeout: Duration = 2.minutes) {
    val file = editor.getVirtualFile()
    val document = editor.getDocument()
    var errors = emptyList<String?>()

    waitFor(message = "No error highlights in '${file.getName()}'",
            timeout = timeout,
            interval = 5.seconds,
            errorMessage = { "Found ${errors.size} error highlight(s) in '${file.getName()}': $errors" }) {
      driver.waitForCodeAnalysis(driver.singleProject(), file)
      errors = driver.getHighlights(document)
        .filter { it.getSeverity().getName() == "ERROR" }
        .map { it.getDescription() }
      errors.isEmpty()
    }
  }
}
