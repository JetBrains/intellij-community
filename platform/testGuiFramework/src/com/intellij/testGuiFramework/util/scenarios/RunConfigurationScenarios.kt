// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.step
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

fun RunConfigurationScenarios.openRunConfiguration(configurationName: String) {
  with(guiTestCase) {
    ideFrame {
      step("check presence of run/debug configuration `$configurationName`") {
        navigationBar {
          assert(exists { button(configurationName) }) { "Button `$configurationName` not found on Navigation bar" }
        }
        GuiTestUtilKt.waitUntil("Menu item '${RunConfigurationScenarios.Constants.editConfigurationMenuItem}' is enabled",
                                Timeouts.minutes05) {
          shortcut(Key.ESCAPE)
          button(configurationName).click()
          popupMenu(RunConfigurationScenarios.Constants.editConfigurationMenuItem, Timeouts.noTimeout).isSearchedItemEnable()
        }
        popupMenu(RunConfigurationScenarios.Constants.editConfigurationMenuItem).clickSearchedItem()
      }
    }
  }
}


fun RunConfigurationScenarios.checkRunConfiguration(
  expectedValues: Map<RunConfigurationModel.ConfigurationField, String>,
  vararg configuration: String) {
  step("check values in 'Run/Debug Configuration' dialog") {
    with(guiTestCase) {
      openRunConfiguration(configuration.last())
      runConfigModel.checkConfigurationExistsAndSelect(*configuration)
      for ((field, expectedValue) in expectedValues) {
        runConfigModel.checkOneValue(field, expectedValue)
      }
      runConfigModel.closeWithCancel()
    }
  }
}

fun RunConfigurationScenarios.changeRunConfiguration(
  changedValues: Map<RunConfigurationModel.ConfigurationField, String>,
  vararg configuration: String) {
  with(guiTestCase) {
    openRunConfiguration(configuration.last())
    runConfigModel.checkConfigurationExistsAndSelect(*configuration)
    for ((field, changedValue) in changedValues) {
      runConfigModel.changeOneValue(field, changedValue)
    }
    runConfigModel.closeWithOK()
  }
}
