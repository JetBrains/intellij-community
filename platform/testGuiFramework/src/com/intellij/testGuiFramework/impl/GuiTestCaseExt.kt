// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.testGuiFramework.fixtures.ActionButtonFixture
import com.intellij.testGuiFramework.fixtures.GutterFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedJTreePathFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.framework.toPrintable
import com.intellij.testGuiFramework.util.*
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import java.awt.IllegalComponentStateException

open class GuiTestCaseExt : GuiTestCase() {

  @Rule
  @JvmField
  val testMethod = TestName()

  @Rule
  @JvmField
  val screenshotsDuringTest = ScreenshotsDuringTest(1000) // = 1 sec

  @Rule
  @JvmField
  val logActionsDuringTest = LogActionsDuringTest()

  val projectFolder: String by lazy {
    projectsFolder.newFolder(testMethod.methodName).canonicalPath
  }

//  @Rule
//  @JvmField
//  val collector = object : ErrorCollector() {
//    override fun addError(error: Throwable?) {
//      val screenshotName = testName + "." + testMethod.methodName
//      takeScreenshotOnFailure(error, screenshotName)
//      super.addError(error)
//    }
//  }

  @Before
  open fun setUp() {
    logStartTest(testMethod.methodName)
  }

  @After
  fun tearDown() {
    logEndTest(testMethod.methodName)
  }

  open fun isIdeFrameRun(): Boolean = true
}

fun <T> ErrorCollector.checkThat(value: T, matcher: Matcher<T>, reason: () -> String) {
  checkThat(reason(), value, matcher)
}

/**
 * Closes the current project
 * */
fun GuiTestCase.closeProject() {
  ideFrame {
    logUIStep("Close the project")
    waitAMoment()
    closeProject()
  }
}

/**
 * Provide waiting for background tasks to finish
 * This function should be used instead of  [waitForBackgroundTasksToFinish]
 * because sometimes the latter doesn't wait enough time
 * */
fun GuiTestCase.waitAMoment(attempts: Int = 0) {
  val maxAttempts = 3
  ideFrame {
    this.waitForBackgroundTasksToFinish()
    val asyncIcon = indexingProcessIconNullable(Timeouts.noTimeout)
    if(asyncIcon != null){
      val timeoutForBackgroundTasks = Timeouts.minutes10
      try {
        asyncIcon.click()
        waitForPanelToDisappear(
          panelTitle = "Background Tasks",
          timeoutToAppear = Timeouts.seconds01,
          timeoutToDisappear = timeoutForBackgroundTasks
        )
      }
      catch (e: IllegalStateException){
        // asyncIcon searched earlier might disappear at all (it's ok)
        // or new one is shown. So let's try to search it again
        if(attempts < maxAttempts)
          waitAMoment(attempts + 1)
        else{
          if(indexingProcessIconNullable(Timeouts.noTimeout) !=null)
            throw WaitTimedOutError("Async icon is shown, but we cannot click on it after $maxAttempts attempts")
        }
      }
      catch (e: IllegalComponentStateException){
        // do nothing - asyncIcon disappears, background process has stopped
      }
      catch (e: ComponentLookupException){
        // do nothing - panel hasn't appeared and it seems ok
      }
      catch (e: WaitTimedOutError) {
        throw WaitTimedOutError("Background process hadn't finished after ${timeoutForBackgroundTasks.toPrintable()}")
      }
    }
  }
  robot().waitForIdle()
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
 *  - the gradle tool window contains the project tree. But if reimporting fails the tree is empty.
 *
 *  @param waitForProject true if we expect reimporting successful
 *  @param waitForProject false if we expect reimporting failing and the tree window is expected empty
 *  @param rootPath root name expected to be shown in the tree. Checked only if [waitForProject] is true
 * */
fun GuiTestCase.waitForGradleReimport(rootPath: String, waitForProject: Boolean){
  GuiTestUtilKt.waitUntil("for gradle reimport finishing", timeout = Timeouts.minutes05){
    var result = false
    try {
      ideFrame {
        toolwindow(id = "Gradle") {
          content(tabName = "") {
            // first, check whether the action button "Refresh all external projects" is enabled
            val text = "Refresh all external projects"
            val isReimportButtonEnabled = try {
              val fixtureByTextAnyState = ActionButtonFixture.fixtureByTextAnyState(this.target(), robot(), text)
              assertTrue("Gradle refresh button should be visible and showing", this.target().isShowing && this.target().isVisible)
              fixtureByTextAnyState.isEnabled
            }
            catch (e: Exception) {
              logInfo("$currentTimeInHumanString: waitForGradleReimport.actionButton: ${e::class.simpleName} - ${e.message}")
              false
            }
            // second, check that Gradle tool window contains a tree with the specified [rootPath]
            val gradleWindowHasPath = if(waitForProject){
              try {
                jTree(rootPath, timeout = Timeouts.noTimeout).hasPath()
              }
              catch (e: Exception) {
                logInfo("$currentTimeInHumanString: waitForGradleReimport.jTree: ${e::class.simpleName} - ${e.message}")
                false
              }
            }
            else true
            // calculate result whether to continue waiting
            result = gradleWindowHasPath && isReimportButtonEnabled
          }
        }
        // check status in the Build tool window
        var syncState = !waitForProject
        if(waitForProject) {
          toolwindow(id = "Build") {
            content(tabName = "Sync") {
              val tree = treeTable().target.tree
              val treePath = ExtendedJTreePathFinder(tree).findMatchingPath(listOf(this@ideFrame.project.name + ":"))
              val state = ExtendedJTreeCellReader().valueAtExtended(tree, treePath) ?: ""
              syncState = state.contains("sync finished")
            }
          }
        }
        // final calculating of result
        result = result && syncState
      }
    }
    catch (ignore: Exception) {}
    result
  }

}

fun GuiTestCase.gradleReimport() {
  logTestStep("Reimport gradle project")
  ideFrame {
    toolwindow(id = "Gradle") {
      content(tabName = "") {
        waitAMoment()
        actionButton("Refresh all external projects", timeout = Timeouts.minutes05).click()
      }
    }
  }
}

fun GuiTestCase.mavenReimport() {
  logTestStep("Reimport maven project")
  ideFrame {
    toolwindow(id = "Maven") {
      content(tabName = "") {
        val button = actionButton("Reimport All Maven Projects")
        Pause.pause(object : Condition("Wait for button Reimport All Maven Projects to be enabled.") {
          override fun test(): Boolean {
            return button.isEnabled
          }
        }, Timeouts.minutes02)
        robot().waitForIdle()
        button.click()
        robot().waitForIdle()
      }
    }
  }
}

fun GuiTestCase.checkProjectIsCompiled(expectedStatus: String) {
  val textEventLog = "Event Log"
  ideFrame {
    logTestStep("Going to check how the project compiles")
    invokeMainMenu("CompileProject")
    waitAMoment()
    toolwindow(id = textEventLog) {
      content(tabName = "") {
        editor{
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

fun GuiTestCase.checkProjectIsRun(configuration: String, message: String) {
  val buttonRun = "Run"
  logTestStep("Going to run configuration `$configuration`")
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

fun GuiTestCase.checkGutterIcons(gutterIcon: GutterFixture.GutterIcon,
                                 expectedNumberOfIcons: Int,
                                 expectedLines: List<String>) {
  ideFrame {
    logTestStep("Going to check whether $expectedNumberOfIcons $gutterIcon gutter icons are present")
    editor {
      waitUntilFileIsLoaded()
      waitUntilErrorAnalysisFinishes()
      gutter.waitUntilIconsShown(mapOf(gutterIcon to expectedNumberOfIcons))
      val gutterLinesWithIcon = gutter.linesWithGutterIcon(gutterIcon)
      val contents = this@editor.getCurrentFileContents(false)?.lines() ?: listOf()
      for ((index, line) in gutterLinesWithIcon.withIndex()) {
        // line numbers start with 1, but index in the contents list starts with 0
        val currentLine = contents[line - 1]
        val expectedLine = expectedLines[index]
        assert(currentLine.contains(expectedLine)) {
          "At line #$line the actual text is `$currentLine`, but it was expected `$expectedLine`"
        }
      }
    }
  }
}
