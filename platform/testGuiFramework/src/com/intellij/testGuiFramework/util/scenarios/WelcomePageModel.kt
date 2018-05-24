// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.util.scenarios.WelcomePageDialogModel.Constants.actionCreateNewProject
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class WelcomePageDialogModel(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<WelcomePageDialogModel>(
    { WelcomePageDialogModel(it) }
  )
  object Constants{
    const val actionCreateNewProject = "Create New Project"
    const val actionImportProject = "Create Import Project"
    const val actionOpen = "Open"
    const val actionCheckout = "Check out from Version Control"

    const val menuConfigure = "Configure"
  }
}

val GuiTestCase.welcomePageDialogModel by WelcomePageDialogModel

fun WelcomePageDialogModel.createNewProject(){
  with(testCase){
    welcomeFrame {
      logTestStep(actionCreateNewProject)
      actionLink(actionCreateNewProject).click()
    }
  }
}
