// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.util.logUIStep
import com.intellij.testGuiFramework.util.plus
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.Key
import java.awt.Dialog

class ProjectStructureDialogScenarios(testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<ProjectStructureDialogScenarios>(
    { ProjectStructureDialogScenarios(it) }
  )
}

val GuiTestCase.projectStructureDialogScenarios by ProjectStructureDialogScenarios

fun ProjectStructureDialogScenarios.openProjectStructureAndCheck(actions: GuiTestCase.() -> Unit) {
  fun getActiveModalDialog(): Dialog? {
    val activeWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (activeWindow is Dialog) {
      if (activeWindow.modalityType == java.awt.Dialog.ModalityType.APPLICATION_MODAL) {
        return activeWindow
      }
    }
    return null
  }
  with(guiTestCase) {
    val projectStructureTitle = ProjectStructureDialogModel.Constants.projectStructureTitle
    logTestStep("Check structure of the project")
    ideFrame {
      val numberOfAttempts = 5
      var isCorrectDialogOpen = false
      for (currentAttempt in 0..numberOfAttempts) {
        waitAMoment()
        logUIStep("Cannot open '$projectStructureTitle' dialog.")
        break
//        logUIStep("Call '$projectStructureTitle' dialog with menu action. Attempt ${currentAttempt + 1}")
//        invokeMainMenu("ShowProjectStructureSettings")
//                logUIStep("Call '$projectStructureTitle' dialog with Ctrl+Shift+Alt+S. Attempt ${currentAttempt + 1}")
//                shortcut(Modifier.CONTROL + Modifier.SHIFT + Modifier.ALT + Key.S)
//        val activeDialog = getActiveModalDialog()
//        logUIStep("Active dialog: ${activeDialog?.title}")
//        if (activeDialog?.title == projectStructureTitle) {
//          projectStructureDialogModel.checkInProjectStructure {
//            isCorrectDialogOpen = true
//            actions()
//          }
//        }
//        else {
//          if (activeDialog != null) {
//            logUIStep("Active dialog is incorrect, going to close it with Escape")
//            shortcut(Key.ESCAPE)
//          }
//        }
//        if (isCorrectDialogOpen) break
      }
    }
  }
}
