// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.testGuiFramework.util.*
import org.fest.swing.timing.Pause
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName

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
