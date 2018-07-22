// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.popupClick
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class RunConfigurationScenarios(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<RunConfigurationScenarios>(
    { RunConfigurationScenarios(it) }
  )

  object Constants {
    const val editConfigurationMenuItem = "Edit Configurations..."
  }
}

val GuiTestCase.runConfigScenarios by RunConfigurationScenarios


fun RunConfigurationScenarios.openRunConfiguration(vararg configuration: String) {
  val configurationName = configuration.last()
  with(guiTestCase) {
    ideFrame {
      logTestStep("Going to check presence of run/debug configuration `$configurationName`")
      navigationBar {
        assert(exists { button(configurationName) }) { "Button `$configurationName` not found on Navigation bar" }
        button(configurationName).click()
        popupClick(RunConfigurationScenarios.Constants.editConfigurationMenuItem)
      }
    }
  }
}


fun RunConfigurationScenarios.checkRunConfiguration(expectedValues: Map<String, String>, vararg configuration: String) {
  with(guiTestCase) {
    openRunConfiguration(*configuration)
    runConfigModel.checkConfigurationExistsAndSelect(*configuration)
    for ((field, expectedValue) in expectedValues) {
      runConfigModel.checkOneValue(field, expectedValue)
    }
    runConfigModel.closeWithCancel()
  }
}

fun RunConfigurationScenarios.changeRunConfiguration(changedValues: Map<String, String>, vararg configuration: String) {
  with(guiTestCase) {
    openRunConfiguration(*configuration)
    runConfigModel.checkConfigurationExistsAndSelect(*configuration)
    for ((field, changedValue) in changedValues) {
      runConfigModel.changeOneValue(field, changedValue)
    }
    runConfigModel.closeWithOK()
  }
}
