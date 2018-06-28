// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class ProjectStructureDialogScenarios(testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<ProjectStructureDialogScenarios>(
    { ProjectStructureDialogScenarios(it) }
  )
}

val GuiTestCase.projectStructureDialogScenarios by ProjectStructureDialogScenarios

fun ProjectStructureDialogScenarios.openProjectStructureAndCheck(actions: GuiTestCase.() -> Unit) {
  with(guiTestCase) {
    val projectStructureTitle = ProjectStructureDialogModel.Constants.projectStructureTitle
    logTestStep("Check structure of the project")
    ideFrame {
      projectView {
        this.activate()
        click()
        shortcut(Key.HOME)
      }
      val numberOfAttempts = 5
      var isCorrectDialogOpen = false
      for (currentAttempt in 0..numberOfAttempts) {
        waitAMoment()
        logUIStep("Call '$projectStructureTitle' dialog with menu action. Attempt ${currentAttempt + 1}")
        invokeMainMenu("ShowProjectStructureSettings")
                logUIStep("Call '$projectStructureTitle' dialog with Ctrl+Shift+Alt+S. Attempt ${currentAttempt + 1}")
                shortcut(Modifier.CONTROL + Modifier.SHIFT + Modifier.ALT + Key.S)
        val activeDialog = GuiTestUtil.getActiveModalDialog()
        logUIStep("Active dialog: ${activeDialog?.title}")
        if (activeDialog?.title == projectStructureTitle) {
          projectStructureDialogModel.checkInProjectStructure {
            isCorrectDialogOpen = true
            actions()
          }
        }
        else {
          if (activeDialog != null) {
            logUIStep("Active dialog is incorrect, going to close it with Escape")
            shortcut(Key.ESCAPE)
          }
        }
        if (isCorrectDialogOpen) break
      }
    }
  }
}
