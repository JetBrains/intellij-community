// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.actionButton
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.table
import com.intellij.testGuiFramework.util.Clipboard
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class EnvironmentVariablesModel(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<EnvironmentVariablesModel>(
    { EnvironmentVariablesModel(it) }
  )

  object Constants {
    const val envVarsTitle = "Environment Variables"

    const val buttonAdd = "Add"
    const val buttonPaste = "Paste"
    const val buttonRemove = "Remove"
    const val buttonOK = "OK"
    const val buttonCancel = "Cancel"

    val pattern = Regex("^([a-zA-Z]\\S*)\\s*=\\s*(.+)")
  }
}

val GuiTestCase.envVarsModel by EnvironmentVariablesModel

fun EnvironmentVariablesModel.connectDialog(): JDialogFixture =
  testCase.dialog(EnvironmentVariablesModel.Constants.envVarsTitle, true, GuiTestUtil.defaultTimeout)

fun EnvironmentVariablesModel.paste(property: String) {
  assert(property.contains(EnvironmentVariablesModel.Constants.pattern))
  { "Property `$property` doesn't match to the property format \"name = value\"" }
  with(connectDialog()) {
    Clipboard.copyToClipboard(property)
    actionButton(EnvironmentVariablesModel.Constants.buttonPaste).click()
    guiTestCase.robot().waitForIdle()
    button(EnvironmentVariablesModel.Constants.buttonOK).click()
  }
}

fun EnvironmentVariablesModel.parseValues(text: String): Map<String, String> {
  return text
    .split(";")
    .map {
      Pair(EnvironmentVariablesModel.Constants.pattern.find(it)?.groupValues!![1],
           EnvironmentVariablesModel.Constants.pattern.find(it)?.groupValues!![2])
    }
    .toMap()
}

fun EnvironmentVariablesModel.removeAll(currentText: String) {
  if (currentText.isEmpty()) return
  with(connectDialog()) {
    parseValues(currentText)
      .keys
      .forEach {
        table(it).cell(it).click()
        actionButton(EnvironmentVariablesModel.Constants.buttonRemove).click()
      }
    button(EnvironmentVariablesModel.Constants.buttonOK).click()
  }
}