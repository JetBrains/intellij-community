// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.exception.ComponentLookupException

class RunConfigurationModel(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<RunConfigurationModel>(
    { RunConfigurationModel(it) }
  )

  object Constants {
    const val runConfigTitle = "Run/Debug Configurations"
    const val buttonCancel = "Cancel"
    const val buttonOK = "OK"

  }
}

val GuiTestCase.runConfigModel by RunConfigurationModel

fun RunConfigurationModel.connectDialog(): JDialogFixture =
  testCase.dialog(RunConfigurationModel.Constants.runConfigTitle, true, GuiTestUtil.defaultTimeout)

fun RunConfigurationModel.checkConfigurationExistsAndSelect(vararg configuration: String) {
  with(connectDialog()) {
    guiTestCase.logTestStep("Going to check that configuration '${configuration.joinToString()}' exists")
    assert(guiTestCase.exists { jTree(*configuration) })
    jTree(*configuration).clickPath()
  }
}

fun RunConfigurationModel.closeWithCancel() {
  with(connectDialog()) {
    button(RunConfigurationModel.Constants.buttonCancel).click()
  }
}

fun RunConfigurationModel.closeWithOK() {
  with(connectDialog()) {
    button(RunConfigurationModel.Constants.buttonOK).click()
  }
}

fun RunConfigurationModel.checkOneValue(expectedField: String, expectedValue: String) {
  guiTestCase.logTestStep("Going to check that field `$expectedField`has a value = `$expectedValue`")
  with(connectDialog()) {
    val actualValue = when {
      guiTestCase.exists { textfield(expectedField, timeout = 1) } -> {
        textfield(expectedField).text()
      }
      guiTestCase.exists { combobox(expectedField, timeout = 1) } -> {
        val combo = combobox(expectedField)
        combo.selectedItem()
      }
      guiTestCase.exists { checkbox(expectedField, timeout = 1) } -> {
        checkbox(expectedField).isSelected.toString()
      }
      else -> throw ComponentLookupException("Cannot find component with label `$expectedField`")
    }
    guiTestCase.logInfo("Field `$expectedField`: actual value = `$actualValue`, expected value = `$expectedValue`")
    assert(actualValue == expectedValue) {
      "Field `$expectedField`: actual value = `$actualValue`, expected value = `$expectedValue`"
    }
  }
}

fun RunConfigurationModel.changeOneValue(expectedField: String, newValue: String) {
  guiTestCase.logTestStep("Going to set field `$expectedField`to a value = `$newValue`")
  val dialog = connectDialog()
  when {
    guiTestCase.exists { dialog.textfield(expectedField, timeout = 1) } -> {
      dialog.textfield(expectedField).setText(newValue)
    }
    guiTestCase.exists { dialog.combobox(expectedField, timeout = 1) } -> {
      dialog.combobox(expectedField).selectItem(newValue)
    }
    guiTestCase.exists { dialog.checkbox(expectedField, timeout = 1) } -> {
      val newBooleanValue = newValue.toBoolean()
      if (dialog.checkbox(expectedField).isSelected != newBooleanValue)
        dialog.checkbox(expectedField).isSelected = newBooleanValue
    }
    else -> throw ComponentLookupException("Cannot find component with label `$expectedField`")
  }
}