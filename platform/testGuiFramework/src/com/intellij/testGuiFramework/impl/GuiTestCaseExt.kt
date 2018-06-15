// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.testGuiFramework.fixtures.GutterFixture
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.util.*
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Pause
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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

  @get:Rule
  val testRootPath: TemporaryFolder by lazy {
    TemporaryFolder()
  }
  val projectFolder: String by lazy {
    testRootPath.newFolder(testMethod.methodName).canonicalPath
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
    guiTestRule.IdeHandling().setUp()
    logStartTest(testMethod.methodName)
  }

  @After
  fun tearDown() {
    if (isIdeFrameRun())
      closeProject()
    logEndTest(testMethod.methodName)
    guiTestRule.IdeHandling().tearDown()
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
 * Wrapper for [waitForBackgroundTasksToFinish]
 * adds an extra pause
 * This function should be used instead of  [waitForBackgroundTasksToFinish]
 * because sometimes it doesn't wait enough time
 * After [waitForBackgroundTasksToFinish] fixing this function should be removed
 * @param extraTimeOut time of additional waiting
 * */
fun GuiTestCase.waitAMoment(extraTimeOut: Long = 2000L) {
  ideFrame {
    this.waitForBackgroundTasksToFinish()
  }
  Pause.pause(extraTimeOut)
}

/**
 * Performs test whether the specified item exists in a tree
 * Note: the dialog with the investigated tree must be open
 * before using this test
 * @param expectedItem - expected exact item
 * @param name - name of item kind, such as "Library" or "Facet". Used for understandable error message
 * */
fun GuiTestCase.testTreeItemExist(name: String, vararg expectedItem: String) {
  ideFrame {
    logInfo("Check that $name -> ${expectedItem.joinToString(" -> ")} exists in a tree element")
    kotlin.assert(exists { jTree(*expectedItem) }) { "$name '${expectedItem.joinToString(", ")}' not found" }
  }
}


fun ExtendedTreeFixture.selectWithKeyboard(testCase: GuiTestCase, vararg path: String) {
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


fun GuiTestCase.gradleReimport() {
  logTestStep("Reimport gradle project")
  ideFrame {
    toolwindow(id = "Gradle") {
      content(tabName = "projects") {
        //        waitAMoment()
        actionButton("Refresh all external projects").click()
      }
    }
  }
}

fun GuiTestCase.mavenReimport() {
  logTestStep("Reimport maven project")
  ideFrame {
    toolwindow(id = "Maven Projects") {
      content(tabName = "") {
        actionButton("Reimport All Maven Projects").click()
      }
    }
  }
}

fun GuiTestCase.checkProjectIsCompiled(expectedStatus: String) {
  val textEventLog = "Event Log"
  ideFrame {
    logTestStep("Going to check how the project compiles")
    invokeMainMenu("CompileProject")
    shortcut(Modifier.CONTROL + Modifier.SHIFT + Key.A)
    waitAMoment()
    typeText(textEventLog)
    waitAMoment()
    shortcut(Key.ENTER)
    toolwindow(id = textEventLog) {
      content(tabName = "") {
        editor{
          val lastLine = this.getCurrentFileContents(false)?.lines()?.last { it.trim().isNotEmpty() } ?: ""
          assert(lastLine.contains(expectedStatus)) {
            "Line `$lastLine` doesn't contain expected status `$expectedStatus`"
          }
        }
      }
    }
  }
}

fun GuiTestCase.checkRunConfiguration(expectedValues: Map<String, String>, vararg configuration: String) {
  val cfgName = configuration.last()
  val runDebugConfigurations = "Run/Debug Configurations"
  ideFrame {
    logTestStep("Going to check presence of Run/Debug configuration `$cfgName`")
    navigationBar {
      assert(exists { button(cfgName) }) { "Button `$cfgName` not found on Navigation bar" }
      button(cfgName).click()
      popupClick("Edit Configurations...")
    }
    dialog(runDebugConfigurations) {
      assert(exists { jTree(*configuration) })
      jTree(*configuration).clickPath(*configuration)
      for ((field, expectedValue) in expectedValues) {
        logTestStep("Field `$field`has a value = `$expectedValue`")
        checkOneValue(this@checkRunConfiguration, field, expectedValue)
      }
      button("Cancel").click()
    }
  }
}

fun JDialogFixture.checkOneValue(guiTestCase: GuiTestCase, expectedField: String, expectedValue: String){
  val actualValue = when {
    guiTestCase.exists {textfield(expectedField)} -> textfield(expectedField).text()
    guiTestCase.exists { combobox(expectedField) } -> combobox(expectedField).selectedItem()
    else -> throw ComponentLookupException("Cannot find component with label `$expectedField`")
  }
  assert(actualValue == expectedValue) {
    "Field `$expectedField`: actual value = `$actualValue`, expected value = `$expectedValue`"
  }
}

fun GuiTestCase.checkProjectIsRun(configuration: String, message: String){
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
          val output = this.getCurrentFileContents(false)?.lines()?.filter { it.trim().isNotEmpty() } ?: listOf()
          assert(output.firstOrNull { it.contains(message) } != null){
            "Run output:\n" +
            "\t${output.joinToString("\n\t")}\n" +
            "doesn't contain expected message `$message`"
          }
        }
      }
    }
  }
}

fun GuiTestCase.checkRunGutterIcons(expectedNumberOfRunIcons: Int, expectedRunLines: List<String>){
  ideFrame {
    logTestStep("Going to check whether $expectedNumberOfRunIcons `Run` gutter icons are present")
    GuiTestUtilKt.runOnEdt {
      editor {
        assert(gutter.isGutterIconPresent(GutterFixture.GutterIcon.RUN_SCRIPT)) {
          "No `Run` icons found on gutter panel"
        }
        val gutterRunLines = gutter.linesWithGutterIcon(GutterFixture.GutterIcon.RUN_SCRIPT)
        assert(gutterRunLines.size == expectedNumberOfRunIcons) {
          "Found ${gutterRunLines.size} gutter icons `Run`, but expected $expectedNumberOfRunIcons"
        }
        val contents = this@editor.getCurrentFileContents(false)?.lines() ?: listOf()
        for ((index, line) in gutterRunLines.withIndex()) {
          // line numbers start with 1, but index in the contents list starts with 0
          val currentLine = contents[line - 1]
          val expectedLine = expectedRunLines[index]
          assert(currentLine.contains(expectedLine)) {
            "At line #$line the actual text is `$currentLine`, but it was expected `$expectedLine`"
          }
        }
        waitUntilErrorAnalysisFinishes()
      }
    }
  }
}

fun GuiTestCase.checkFileExists(filePath: String){
  logTestStep("Going to check whether file `$filePath` created")
  assert(File(filePath).exists()) { "Can't find a file `$filePath`" }
}

fun GuiTestCase.checkFileContainsLine(filePath: String, line: String){
  val inputFile = Paths.get(filePath)
  logTestStep("Going to check whether ${inputFile.fileName} contains line `$line`")
  assert(Files.readAllLines(inputFile).contains(line)) { "Line `$line` not found" }
}