// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.driver.FinderPredicate
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.*
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

  enum class FieldKind {
    Text, Check, Choice, List, Tree, Combo, Custom
  }

  data class ConfigurationField(
    val title: String,
    val kind: FieldKind,
    val type: String? = null,
    val predicate: FinderPredicate = predicateEquality) {

    companion object {
      val predicateEquality: FinderPredicate = { left: String, right: String -> left == right }
      val predicateStartsWith: FinderPredicate = { left: String, right: String -> left.startsWith(right) }
    }

    override fun toString(): String {
      return "$title : $kind${if (type != null) " ($type)" else ""}"
    }

    fun RunConfigurationModel.isFieldPresent(): Boolean {
      with(connectDialog()) {
        return when (kind) {
          RunConfigurationModel.FieldKind.Text -> guiTestCase.exists { textfield(title, timeout = 1) }
          RunConfigurationModel.FieldKind.Check -> guiTestCase.exists { checkbox(title, timeout = 1) }
          RunConfigurationModel.FieldKind.Choice -> TODO()
          RunConfigurationModel.FieldKind.List -> TODO()
          RunConfigurationModel.FieldKind.Tree -> TODO()
          RunConfigurationModel.FieldKind.Combo -> guiTestCase.exists { combobox(title, timeout = 1) }
          RunConfigurationModel.FieldKind.Custom -> TODO()
        }
      }
    }

    fun RunConfigurationModel.getFieldValue(): String {
      with(connectDialog()) {
        val actualValue = when (kind) {
          FieldKind.Text -> textfield(title).text()
          FieldKind.Check -> checkbox(title).isSelected.toString()
          FieldKind.Choice -> TODO()
          FieldKind.List -> TODO()
          FieldKind.Tree -> TODO()
          FieldKind.Combo -> combobox(title).selectedItem()
          FieldKind.Custom -> TODO()
        }
        return actualValue ?: throw ComponentLookupException("Cannot find component with label `$title`")
      }
    }

    fun RunConfigurationModel.setFieldValue(value: String) {
      with(connectDialog()) {
        when (kind) {
          FieldKind.Text -> textfield(title).setText(value)
          FieldKind.Check -> checkbox(title).isSelected = value.toBoolean()
          FieldKind.Choice -> TODO()
          FieldKind.List -> TODO()
          FieldKind.Tree -> TODO()
          FieldKind.Combo -> combobox(title).selectItem(value)
          FieldKind.Custom -> TODO()
        }
      }
    }
  }

  enum class ApplicationFields(val conf: ConfigurationField) {
    MainClass(ConfigurationField("Main class:", FieldKind.Text)),
    VMOptions(ConfigurationField("VM options:", FieldKind.Text)),
    ProgramArgs(ConfigurationField("Program arguments:", FieldKind.Text)),
    WorkingDir(ConfigurationField("Working directory:", FieldKind.Text)),
    EnvVars(ConfigurationField("Environment variables:", FieldKind.Text)),
    UseModule(ConfigurationField("Use classpath of module:", FieldKind.Combo)),
    ProvidedScope(ConfigurationField("Include dependencies with \"Provided\" scope", FieldKind.Check)),
    JRE(ConfigurationField("JRE:", FieldKind.Combo, predicate = ConfigurationField.predicateStartsWith)),
    ShortenCmdLine(ConfigurationField("Shorten command line:", FieldKind.Combo,
                                      type = "com.intellij.execution.ui.ShortenCommandLineModeCombo",
                                      predicate = ConfigurationField.predicateStartsWith)),
    CapturingSnapshots(ConfigurationField("Enable capturing form snapshots", FieldKind.Check)),
    BeforeLaunch(ConfigurationField("Before launch:", FieldKind.List))
  }

  enum class GlassfishFields(val conf: ConfigurationField) {
    AppServer(ConfigurationField("Application server:", FieldKind.Combo)),
    URL(ConfigurationField("URL:", FieldKind.Text)),
    AfterLaunchCheck(ConfigurationField("After launch", FieldKind.Check)),
    OnUpdateAction(ConfigurationField("On 'Update' action:", FieldKind.Combo)),
    ServerDomain(ConfigurationField("Server Domain:", FieldKind.Combo)),
    Username(ConfigurationField("Username:", FieldKind.Text)),
    Password(ConfigurationField("Password:", FieldKind.Text))
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

fun RunConfigurationModel.checkOneValue(expectedField: RunConfigurationModel.ConfigurationField, expectedValue: String) {
  with(expectedField) {
    if (!isFieldPresent()) throw ComponentLookupException("Cannot find component with label `$title`")
    val actualValue = getFieldValue()
    guiTestCase.logTestStep("Field `$title`: actual value = `$actualValue`, expected value = `$expectedValue`")
    assert(predicate(actualValue, expectedValue)) {
      "Field `$title`: actual value = `$actualValue`, expected value = `$expectedValue`"
    }
  }
}

fun RunConfigurationModel.changeOneValue(expectedField: RunConfigurationModel.ConfigurationField, newValue: String) {
  with(expectedField) {
    if (!isFieldPresent()) throw ComponentLookupException("Cannot find component with label `$title`")
    guiTestCase.logTestStep("Going to set field `${expectedField.title}`to a value = `$newValue`")
    setFieldValue(newValue)
  }
}

fun RunConfigurationModel.printHierarchy() {
  with(connectDialog()) {
    println(ScreenshotOnFailure.getHierarchy())
  }
}