// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.testGuiFramework.fixtures.ActionButtonFixture
import com.intellij.testGuiFramework.fixtures.GutterFixture
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedJTreePathFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.framework.toPrintable
import com.intellij.testGuiFramework.util.*
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.exception.WaitTimedOutError
import org.hamcrest.Matcher
import org.junit.Assert.assertTrue
import org.junit.rules.ErrorCollector
import java.awt.IllegalComponentStateException
import javax.swing.JTree

fun <T> ErrorCollector.checkThat(value: T, matcher: Matcher<T>, reason: () -> String) {
  checkThat(reason(), value, matcher)
}

/**
 * Closes the current project
 * */
fun GuiTestCase.closeProject() {
  ideFrame {
    step("close the project") {
      waitAMoment()
      closeProject()
    }
  }
}

/**
 * Provide waiting for background tasks to finish
 * This function should be used instead of  [IdeFrameFixture.waitForBackgroundTasksToFinish]
 * because sometimes the latter doesn't wait enough time
 * The function searches for async icon indicator and waits for its disappearing
 * This occurs several times as background processes often goes one after another.
 * */
fun GuiTestCase.waitAMoment() {
  fun isWaitIndicatorPresent(): Boolean {
    var result = false
    ideFrame {
      result = indexingProcessIconNullable(Timeouts.seconds03) != null
    }
    return result
  }

  fun waitBackgroundTaskOneAttempt(attempt: Int) {
    step("wait for background task - attempt #$attempt") {
      ideFrame {
        this.waitForBackgroundTasksToFinish()
        val asyncIcon = indexingProcessIconNullable(Timeouts.seconds03)
        if (asyncIcon != null) {
          val timeoutForBackgroundTasks = Timeouts.minutes10
          try {
            step("search and click on async icon") {
              asyncIcon.click()
            }
            step("wait for panel 'Background tasks' disappears") {
              waitForPanelToDisappear(
                panelTitle = "Background Tasks",
                timeoutToAppear = Timeouts.seconds01,
                timeoutToDisappear = timeoutForBackgroundTasks
              )
            }
          }
          catch (ignore: NullPointerException) {
            // if asyncIcon disappears at once after getting the NPE from fest might occur
            // but it's ok - nothing to wait anymore
          }
          catch (ignore: IllegalComponentStateException) {
            // do nothing - asyncIcon disappears, background process has stopped
          }
          catch (ignore: ComponentLookupException) {
            // do nothing - panel hasn't appeared and it seems ok
          }
          catch (ignore: IllegalStateException) {
            // asyncIcon searched earlier might disappear at all (it's ok)
          }
          catch (e: WaitTimedOutError) {
            throw WaitTimedOutError("Background process hadn't finished after ${timeoutForBackgroundTasks.toPrintable()}")
          }
        }
        else logInfo("no async icon found - no background process")
      }
      logInfo("attempt #$attempt of waiting for background task finished")
    }
  }

  step("wait for background task") {
    val maxAttemptsWaitForBackgroundTasks = 5
    var currentAttempt = maxAttemptsWaitForBackgroundTasks
    while (isWaitIndicatorPresent() && currentAttempt >= 0) {
      waitBackgroundTaskOneAttempt(maxAttemptsWaitForBackgroundTasks - currentAttempt)
      currentAttempt--
    }
    if (currentAttempt < 0) {
      throw WaitTimedOutError(
        "Background processes still continue after $maxAttemptsWaitForBackgroundTasks attempts to wait for their finishing")
    }
    logInfo("wait for background task finished")
  }
}

/**
 * Performs test whether the specified item exists in a tree
 * Note: the dialog with the investigated tree must be open
 * before using this test
 * @param expectedItem - expected exact item
 * @param name - name of item kind, such as "Library" or "Facet". Used for understandable error message
 * @param predicate - searcher rule, how to compare an item and name. By default they are compared by equality
 * */
fun GuiTestCase.testTreeItemExist(name: String, vararg expectedItem: String, predicate: FinderPredicate = Predicate.equality) {
  ideFrame {
    logInfo("Check that $name -> ${expectedItem.joinToString(" -> ")} exists in a tree element")
    kotlin.assert(exists { jTree(*expectedItem, predicate = predicate) }) { "$name '${expectedItem.joinToString(", ")}' not found" }
  }
}

/**
 * Performs test whether the specified item exists in a list
 * Note: the dialog with the investigated list must be open
 * before using this test
 * @param expectedItem - expected exact item
 * @param name - name of item kind, such as "Library" or "Facet". Used for understandable error message
 * */
fun GuiTestCase.testListItemExist(name: String, expectedItem: String) {
  ideFrame {
    logInfo("Check that $name -> $expectedItem exists in a list element")
    kotlin.assert(exists { jList(expectedItem, timeout = Timeouts.seconds05) }) { "$name '$expectedItem' not found" }
  }
}

/**
 * Performs test whether the specified item exists in a table
 * Note: the dialog with the investigated list must be open
 * before using this test
 * @param expectedItem - expected exact item
 * @param name - name of item kind, such as "Library" or "Facet". Used for understandable error message
 * */
fun GuiTestCase.testTableItemExist(name: String, expectedItem: String) {
  ideFrame {
    logInfo("Check that $name -> $expectedItem exists in a list element")
    kotlin.assert(exists { table(expectedItem, timeout = Timeouts.seconds05) }) { "$name '$expectedItem' not found" }
  }
}

/**
 * Selects specified [path] in the tree by keyboard searching
 * @param path in string form
 * @param testCase - test case is required only because of keyboard related functions
 *
 * TODO: remove [testCase] parameter (so move [shortcut] and [typeText] functions
 * out of GuiTestCase)
 * */
fun ExtendedJTreePathFixture.selectWithKeyboard(testCase: GuiTestCase, vararg path: String) {
  fun currentValue(): String {
    val selectedRow = target().selectionRows.first()
    return valueAt(selectedRow) ?: throw IllegalStateException("Nothing is selected in the tree")
  }
  click()
  testCase.shortcut(Key.HOME) // select the top row
  for((index, step) in path.withIndex()){
    if(currentValue() != step) {
      testCase.typeText(step)
      while (currentValue() != step)
        testCase.shortcut(Key.DOWN)
    }
    if(index < path.size -1) testCase.shortcut(Key.RIGHT)
  }
}

/**
 *  Wait for Gradle reimport finishing
 *  I detect end of reimport by following signs:
 *  - action button "Refresh all external projects" becomes enable. But sometimes it becomes
 *  enable only for a couple of moments and becomes disable again.
 *  - status in the first line in the Build tool window becomes `sync finished` or `sync failed`
 *
 *  @param rootPath root name expected to be shown in the tree. Checked only if [waitForProject] is true
 *  @return status of reimport - true - successful, false - failed
 * */
fun GuiTestCase.waitForGradleReimport(rootPath: String): Boolean {
  val syncSuccessful = "sync finished"
  val syncFailed = "sync failed"
  var reimportStatus = ""

  step("wait for Gradle reimport") {
    GuiTestUtilKt.waitUntil("for gradle reimport finishing", timeout = Timeouts.minutes05) {
      var isReimportButtonEnabled: Boolean = false
      var syncState = false
      try {
        ideFrame {
          toolwindow(id = "Gradle") {
            content(tabName = "") {
              // first, check whether the action button "Refresh all external projects" is enabled
              val text = "Refresh all external projects"
              isReimportButtonEnabled = try {
                val fixtureByTextAnyState = ActionButtonFixture.fixtureByTextAnyState(this.target(), robot(), text)
                assertTrue("Gradle refresh button should be visible and showing", this.target().isShowing && this.target().isVisible)
                fixtureByTextAnyState.isEnabled
              }
              catch (e: Exception) {
                logInfo("$currentTimeInHumanString: waitForGradleReimport.actionButton: ${e::class.simpleName} - ${e.message}")
                false
              }
              logInfo("'$text' button is ${if(isReimportButtonEnabled) "enabled" else "disabled"}")
            }
          }
          // second, check status in the Build tool window
          toolwindow(id = "Build") {
            content(tabName = "Sync") {
              val tree = treeTable().target.tree
              val pathStrings = listOf(rootPath)
              val treePath = try {
                ExtendedJTreePathFinder(tree).findMatchingPathByPredicate(pathStrings = pathStrings, predicate = Predicate.startWith)
              }
              catch (e: LocationUnavailableException) {
                null
              }
              if (treePath != null) {
                reimportStatus = ExtendedJTreeCellReader().valueAtExtended(tree, treePath) ?: ""
                syncState = reimportStatus.contains(syncSuccessful) || reimportStatus.contains(syncFailed)
              }
              else {
                syncState = false
              }
              logInfo("Reimport status is '$reimportStatus', synchronization is ${if(syncState) "finished" else "in process"}")
            }
          }
        }
      }
      catch (ignore: Exception) {}
      // final calculating of result
      val result = isReimportButtonEnabled && syncState
      result
    }
    logInfo("end of waiting for background task")
  }
  return reimportStatus.contains(syncSuccessful)
}

fun GuiTestCase.gradleReimport() {
  step("reimport gradle project") {
    ideFrame {
      toolwindow(id = "Gradle") {
        content(tabName = "") {
          waitAMoment()
          step("click 'Refresh all external projects' button") {
            actionButton("Refresh all external projects", timeout = Timeouts.minutes05).click()
          }
        }
      }
    }
  }
}

fun GuiTestCase.mavenReimport() {
  step("reimport maven project") {
    ideFrame {
      toolwindow(id = "Maven") {
        content(tabName = "") {
          step("search when button 'Reimport All Maven Projects' becomes enable and click it") {
            val reimportAction = "Reimport All Maven Projects"
            val showDepAction = "Show UML Diagram" // but tooltip says "Show Dependencies"
            GuiTestUtilKt.waitUntil("Wait for button '$reimportAction' to be enabled.", timeout = Timeouts.minutes02) {
              actionButton(reimportAction, timeout = Timeouts.seconds30).isEnabled
            }
            try {
              actionButton(showDepAction, timeout = Timeouts.minutes01)
            }
            catch (ignore: ComponentLookupException) {
              logInfo("Maven reimport: not found 'Show Dependencies' button after 1 min waiting")
            }
            robot().waitForIdle()
            actionButton(reimportAction).click()
            robot().waitForIdle()
          }
        }
      }
    }
  }
}

fun GuiTestCase.checkProjectIsCompiled(expectedStatus: String) {
  val textEventLog = "Event Log"
  ideFrame {
    step("check the project compiles") {
      step("invoke main menu 'CompileProject' ") { invokeMainMenu("CompileProject") }
      waitAMoment()
      toolwindow(id = textEventLog) {
        content(tabName = "") {
          editor {
            GuiTestUtilKt.waitUntil("Wait for '$expectedStatus' appears") {
              val output = this.getCurrentFileContents(false)?.lines() ?: emptyList()
              val lastLine = output.lastOrNull { it.trim().isNotEmpty() } ?: ""
              lastLine.contains(expectedStatus)
            }
          }
        }
      }
    }
  }
}

fun GuiTestCase.checkProjectIsRun(configuration: String, message: String) {
  val buttonRun = "Run"
  step("run configuration `$configuration`") {
    ideFrame {
      navigationBar {
        actionButton(buttonRun).click()
      }
      waitAMoment()
      toolwindow(id = buttonRun) {
        content(tabName = configuration) {
          editor {
            GuiTestUtilKt.waitUntil("Wait for '$message' appears") {
              val output = this.getCurrentFileContents(false)?.lines()?.filter { it.trim().isNotEmpty() } ?: listOf()
              logInfo("output: ${output.map { "\n\t$it" }}")
              logInfo("expected message = '$message'")
              output.firstOrNull { it.contains(message) } != null
            }
          }
        }
      }
    }
  }
}

fun GuiTestCase.checkGutterIcons(gutterIcon: GutterFixture.GutterIcon,
                                 expectedNumberOfIcons: Int,
                                 expectedLines: List<String>) {
  ideFrame {
    step("check whether $expectedNumberOfIcons '$gutterIcon' gutter icons are present") {
      editor {
        step("wait for gutter icons appearing") {
          waitUntilFileIsLoaded()
          waitUntilErrorAnalysisFinishes()
          gutter.waitUntilIconsShown(mapOf(gutterIcon to expectedNumberOfIcons))
        }
        val gutterLinesWithIcon = gutter.linesWithGutterIcon(gutterIcon)
        val contents = this@editor.getCurrentFileContents(false)?.lines() ?: listOf()
        for ((index, line) in gutterLinesWithIcon.withIndex()) {
          // line numbers start with 1, but index in the contents list starts with 0
          val currentLine = contents[line - 1]
          val expectedLine = expectedLines[index]
          logInfo("Found line '$currentLine' with icon '$gutterIcon'. Expected line is '$expectedLine'")
          assert(currentLine.contains(expectedLine)) {
            "At line #$line the actual text is `$currentLine`, but it was expected `$expectedLine`"
          }
        }
      }
    }
  }
}

fun GuiTestCase.createJdk(jdkPath: String, jdkName: String = ""): String{
  val dialogName = "Project Structure for New Projects"
  return step("create a JDK on the path `$jdkPath`") {
    lateinit var installedJdkName: String
    welcomeFrame {
      actionLink("Configure").click()
      popupMenu("Structure for New Projects").clickSearchedItem()
      step("open `$dialogName` dialog") {
        dialog(dialogName) {
          jList("SDKs").clickItem("SDKs")
          val sdkTree: ExtendedJTreePathFixture = jTree()

          fun JTree.getListOfInstalledSdks(): List<String> {
            val root = model.root
            return (0 until model.getChildCount(root))
              .map { model.getChild(root, it).toString() }.toList()
          }

          val preInstalledSdks = sdkTree.tree.getListOfInstalledSdks()
          installedJdkName = if (jdkName.isEmpty() || preInstalledSdks.contains(jdkName).not()) {
            actionButton("Add New SDK").click()
            popupMenu("JDK").clickSearchedItem()
            step("open `Select Home Directory for JDK` dialog") {
              dialog("Select Home Directory for JDK") {
                actionButton("Refresh").click()
                step("type the path `$jdkPath`") {
                  typeText(jdkPath)
                }
                step("close `Select Home Directory for JDK` dialog with OK") {
                  button("OK").click()
                }
              }
            }

            val postInstalledSdks = sdkTree.tree.getListOfInstalledSdks()
            postInstalledSdks.first { preInstalledSdks.contains(it).not() }
          }
          else jdkName
          step("close `Default Project Structure` dialog with OK") {
            button("OK").click()
          }
        } // dialog Project Structure
      }
    } // ideFrame
    return@step installedJdkName
  }
}
