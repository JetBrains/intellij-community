package com.intellij.driver.sdk.go.actions

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isCodeAnalysisRunning
import com.intellij.driver.sdk.restartDaemonCodeAnalyzer
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editor.intentionList
import com.intellij.driver.sdk.ui.components.common.editor.openIntentions
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.problemsToolWindow
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForCodeAnalysis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class InspectionActions(val driver: Driver, val ui: UiRobot) {

  /**
   * Invoke the global "Inspect Code" action and start the analysis with the default
   * ("Whole project") scope in the "Specify Inspection Scope" dialog.
   */
  fun runInspectCodeOnWholeProject() {
    step("Run 'Inspect Code' on the whole project") {
      driver.withContext {
        ideFrame { waitForIndicators(INDICATOR_TIMEOUT) }
      }
      driver.invokeAction("InspectCode", now = false)
      ui.dialog(title = SCOPE_DIALOG_TITLE) {
        // "Whole project" is the default scope; just start the analysis.
        button("Analyze").click()
      }
    }
  }

  /**
   * Wait for the "Inspection Results" tool window and assert its tree contains every
   * [expectedTexts] entry (e.g. the "Go" group and a specific inspection name).
   */
  fun verifyInspectionResultsContain(vararg expectedTexts: String, timeout: Duration = RESULTS_TIMEOUT) {
    step("Verify inspection results contain: ${expectedTexts.joinToString()}") {
      driver.withContext {
        ideFrame {
          val resultsTree = inspectionResultsTree()
          resultsTree.waitFound(timeout)
          resultsTree.expandAll(30.seconds)
          expectedTexts.forEach { expected ->
            resultsTree.waitContainsText(expected, timeout = timeout)
          }
        }
      }
    }
  }

  /**
   * Open the "Problems" tool window and assert its (current-file) tree contains every
   * [expectedTexts] entry.
   */
  fun verifyCurrentFileProblemsContain(vararg expectedTexts: String, timeout: Duration = PROBLEMS_TIMEOUT) {
    step("Verify Problems view 'Current File' contains: ${expectedTexts.joinToString()}") {
      driver.withContext {
        ideFrame {
          leftToolWindowToolbar.problemsButton.open()
          problemsToolWindow {
            shouldBe("Problems tool window is not visible", present)
            val problemsTree = tree()
            expectedTexts.forEach { expected ->
              problemsTree.waitContainsText(expected, timeout = timeout)
            }
          }
        }
      }
    }
  }

  /**
   * Wait for code analysis of the focused editor to finish and assert a highlight whose
   * description equals [problemDescription] is present (the inspection is "observed").
   */
  fun observeEditorHighlight(problemDescription: String, timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Observe inspection highlight '$problemDescription' in the editor") {
      driver.withContext {
        ideFrame {
          codeEditor {
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
            var descriptions = emptyList<String?>()
            waitFor(
              message = "Highlight '$problemDescription' didn't appear",
              timeout = timeout,
              errorMessage = { "The editor shows the highlights $descriptions" },
            ) {
              descriptions = getHighlights(editor.getDocument()).map { it.getDescription() }
              problemDescription in descriptions
            }
          }
        }
      }
    }
  }

  /**
   * Place the caret on [caretAnchor], open the intentions popup (Alt+Enter) and apply the
   * [quickFixName] quick fix offered for the [problemDescription] inspection, then wait for
   * re-analysis. Uses the real editor flow so the fix runs inside an undo-transparent command.
   */
  fun applyEditorQuickFix(
    problemDescription: String,
    quickFixName: String,
    caretAnchor: String,
    timeout: Duration = ANALYSIS_TIMEOUT,
  ) {
    step("Apply quick fix '$quickFixName' for inspection '$problemDescription'") {
      driver.withContext {
        ideFrame {
          codeEditor {
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
            var descriptions = emptyList<String?>()
            waitFor(
              message = "Highlight '$problemDescription' didn't appear",
              timeout = timeout,
              errorMessage = { "The editor shows the highlights $descriptions" },
            ) {
              descriptions = getHighlights(editor.getDocument()).map { it.getDescription() }
              problemDescription in descriptions
            }
            clickOn(caretAnchor)
            openIntentions()
          }
          intentionList {
            waitFound(30.seconds)
            clickItem(quickFixName, fullMatch = false)
          }
          codeEditor {
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
          }
        }
      }
    }
  }

  /**
   * Assert that no highlight whose description equals [problemDescription] remains in the
   * focused editor.
   */
  fun assertNoEditorHighlight(problemDescription: String, timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Verify no '$problemDescription' highlight remains in the editor") {
      driver.withContext {
        ideFrame {
          codeEditor {
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
            var descriptions = emptyList<String?>()
            waitFor(
              message = "Highlight '$problemDescription' is still present",
              timeout = timeout,
              errorMessage = { "The editor still shows '$problemDescription' among the highlights $descriptions" },
            ) {
              descriptions = getHighlights(editor.getDocument()).map { it.getDescription() }
              problemDescription !in descriptions
            }
          }
        }
      }
    }
  }

  /**
   * Assert the focused editor text no longer contains any of [fragments] (e.g. the code an
   * applied quick fix was expected to remove).
   */
  fun assertEditorTextDoesNotContain(vararg fragments: String, timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Verify editor text no longer contains: ${fragments.joinToString()}") {
      driver.withContext {
        ideFrame {
          codeEditor {
            fragments.forEach { fragment ->
              var actual = ""
              waitFor(
                message = "Editor still contains '$fragment'",
                timeout = timeout,
                errorMessage = { "Editor still contains '$fragment'. Actual text:\n$actual" },
              ) {
                actual = text
                !actual.contains(fragment)
              }
            }
          }
        }
      }
    }
  }

  /**
   * Replace the whole focused-editor text with [newText] (simulating an edit) and wait for the
   * daemon to finish re-analyzing.
   */
  fun replaceEditorText(newText: String, timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Replace editor text and wait for re-analysis") {
      driver.withContext {
        ideFrame {
          codeEditor {
            text = newText
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
          }
        }
      }
    }
  }

  /** Trigger a fresh analysis pass for the focused editor. */
  fun restartEditorAnalysis(timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Restart analysis of the current editor") {
      driver.withContext {
        ideFrame {
          codeEditor().click()
        }
        driver.restartDaemonCodeAnalyzer("golangci-lint settings changed")
        waitFor(
          message = "Code analysis to start",
          timeout = 10.seconds,
          errorMessage = { "Code analysis did not start within 10 seconds after the restart request" },
        ) {
          driver.isCodeAnalysisRunning()
        }
        ideFrame {
          codeEditor {
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
          }
        }
      }
    }
  }

  /**
   * Wait until code analysis of the focused editor finishes with no ERROR-severity highlights, retrying within
   * [timeout].
   *
   * The check is retried rather than made once: dependency roots attached after the first analysis pass restart the
   * daemon, so the first result of a freshly opened project routinely still holds unresolved references.
   */
  fun assertNoEditorErrorHighlights(timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Verify the editor has no error highlights") {
      driver.withContext {
        ideFrame {
          codeEditor {
            var errors = emptyList<String?>()
            waitFor(message = "The editor has no error highlights",
                    timeout = timeout,
                    errorMessage = { "Found ${errors.size} error highlight(s): $errors" }) {
              waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
              errors = getHighlights(editor.getDocument())
                .filter { it.getSeverity().getName() == "ERROR" }
                .map { it.getDescription() }
              errors.isEmpty()
            }
          }
        }
      }
    }
  }

  /**
   * Wait for code analysis of the focused editor to finish and assert it has at least one
   * ERROR-severity highlight.
   */
  fun assertEditorHasErrorHighlights(timeout: Duration = ANALYSIS_TIMEOUT) {
    step("Verify the editor has error highlights") {
      driver.withContext {
        ideFrame {
          codeEditor {
            waitForCodeAnalysis(file = editor.getVirtualFile(), timeout = timeout)
            var highlights = emptyList<String>()
            waitFor(
              message = "The editor has error highlights",
              timeout = timeout,
              errorMessage = { "No error highlights appeared. The editor shows the highlights $highlights" },
            ) {
              val infos = getHighlights(editor.getDocument())
              highlights = infos.map { "${it.getSeverity().getName()}: ${it.getDescription()}" }
              infos.any { it.getSeverity().getName() == "ERROR" }
            }
          }
        }
      }
    }
  }

  /**
   * Open the "Problems" tool window and assert its (current-file) tree reports no problems.
   *
   * The empty state is a real tree node ("No problems in <file>"), so the check is a positive one:
   * an emptiness check on the collapsed tree would pass while problems are present, because a
   * collapsed tree has no expanded paths to collect.
   */
  fun verifyCurrentFileHasNoProblems(timeout: Duration = PROBLEMS_TIMEOUT) {
    step("Verify Problems view 'Current File' reports no problems") {
      driver.withContext {
        ideFrame {
          leftToolWindowToolbar.problemsButton.open()
          problemsToolWindow {
            shouldBe("Problems tool window is not visible", present)
            var problems: List<String> = emptyList()
            tree().should(
              message = "Problems view reports no problems for the current file",
              timeout = timeout,
              errorMessage = { "Problems view still reports problems: $problems" },
            ) {
              expandAll(EXPAND_TIMEOUT)
              problems = getAllTexts().map { it.text }
              problems.any { it.startsWith(NO_PROBLEMS_NODE_PREFIX) }
            }
          }
          leftToolWindowToolbar.problemsButton.close()
        }
      }
    }
  }

  private fun IdeaFrameUI.inspectionResultsTree(): JTreeUiComponent =
    x(JTreeUiComponent::class.java) { byClass("InspectionTree") }

  companion object {
    private const val SCOPE_DIALOG_TITLE = "Specify Inspection Scope"

    /** Empty-state node of the Problems view tree: "No problems in <file>". */
    private const val NO_PROBLEMS_NODE_PREFIX = "No problems"

    private val INDICATOR_TIMEOUT = 2.minutes
    private val RESULTS_TIMEOUT = 2.minutes
    private val PROBLEMS_TIMEOUT = 1.minutes
    private val ANALYSIS_TIMEOUT = 2.minutes
    private val EXPAND_TIMEOUT = 30.seconds
  }
}
