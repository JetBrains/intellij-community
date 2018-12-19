// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.exception.ComponentLookupException
import javax.swing.JDialog
import javax.swing.JLabel

class RunConfigurationModel(testCase: GuiTestCase) : TestUtilsClass(testCase) {
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

  data class CustomConfigurationField(
    val actionIsPresent: (RunConfigurationModel, String) -> Boolean,
    val actionGetValue: (RunConfigurationModel, String) -> String?,
    val actionSetValue: (RunConfigurationModel, String, String) -> Unit) {
    companion object {

      val envVarsField = CustomConfigurationField(
        actionIsPresent = { model: RunConfigurationModel, title: String ->
          with(model.connectDialog()) {
            model.guiTestCase.exists { textfield(title, timeout = Timeouts.noTimeout) }
          }
        },
        actionSetValue = { model, title: String, value: String ->
          with(model.connectDialog()) {
            val field = componentWithBrowseButton(title)
            field.clickAnyExtensionButton()
            if (value.isNotEmpty()) model.guiTestCase.envVarsModel.paste(value)
            else {
              model.guiTestCase.envVarsModel.removeAll(textfield(title).text() ?: "")
            }
          }
        },
        actionGetValue = { model, title: String ->
          with(model.connectDialog()) {
            textfield(title).text()
          }
        }
      )

      val beforeLaunchField = CustomConfigurationField(
        actionIsPresent = { model: RunConfigurationModel, title: String ->
          with(model.connectDialog()) {
            try {
              findComponentWithTimeout<JLabel, JDialog>(Timeouts.noTimeout) {
                it.isShowing && it.isVisible && (it.text?.startsWith(title) ?: false)
              }.isEnabled
            }
            catch (e: ComponentLookupException) {
              false
            }
          }
        },
        actionSetValue = { _, _: String, _: String ->
          //TODO: support adding of build step
        },
        actionGetValue = { model, title: String ->
          with(model.connectDialog()) {
            try {
              findComponentWithTimeout<JLabel, JDialog>(Timeouts.noTimeout) {
                it.isShowing && it.isVisible && (it.text?.startsWith(title) ?: false)
              }.text.removePrefix(title).trim()
            }
            catch (e: ComponentLookupException) {
              ""
            }
          }
        }
      )
    }

  }

  data class ConfigurationField(
    val title: String,
    val kind: FieldKind,
    val custom: CustomConfigurationField? = null,
    val predicate: FinderPredicate = Predicate.equality) {

    init {
      if (kind == FieldKind.Custom && custom == null)
        throw  IllegalStateException("Handler for custom field '$title' must be set")
    }

    override fun toString() = "$title : $kind"

    fun RunConfigurationModel.isFieldPresent(): Boolean {
      with(connectDialog()) {
        return when (kind) {
          RunConfigurationModel.FieldKind.Text -> guiTestCase.exists { textfield(title, timeout = Timeouts.noTimeout) }
          RunConfigurationModel.FieldKind.Check -> guiTestCase.exists { checkbox(title, timeout = Timeouts.noTimeout) }
          RunConfigurationModel.FieldKind.Choice -> TODO()
          RunConfigurationModel.FieldKind.List -> TODO()
          RunConfigurationModel.FieldKind.Tree -> TODO()
          RunConfigurationModel.FieldKind.Combo -> guiTestCase.exists { combobox(title, timeout = Timeouts.noTimeout) }
          RunConfigurationModel.FieldKind.Custom -> custom?.actionIsPresent?.invoke(this@isFieldPresent, title)
                                                    ?: throw IllegalStateException(
                                                      "Handler for field '$title' not set")
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
          FieldKind.Custom -> custom?.actionGetValue?.invoke(this@getFieldValue, title) ?: throw IllegalStateException(
            "Handler for field '$title' not set")
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
          FieldKind.Combo -> {
            val combo = combobox(title)
            val newValue = combo.listItems().firstOrNull {
              predicate(it, value)
            }
            combo.selectItem(newValue)
          }
          FieldKind.Custom -> custom?.actionSetValue?.invoke(this@setFieldValue, title, value) ?: throw IllegalStateException(
            "Handler for field '$title' not set")
        }
      }
    }
  }

  enum class ApplicationFields(val conf: ConfigurationField) {
    MainClass(ConfigurationField("Main class:", FieldKind.Text)),
    VMOptions(ConfigurationField("VM options:", FieldKind.Text)),
    ProgramArgs(ConfigurationField("Program arguments:", FieldKind.Text)),
    WorkingDir(ConfigurationField("Working directory:", FieldKind.Text)),
    EnvVars(ConfigurationField("Environment variables:", FieldKind.Custom, custom = CustomConfigurationField.envVarsField)),
    UseModule(ConfigurationField("Use classpath of module:", FieldKind.Combo)),
    ProvidedScope(ConfigurationField("Include dependencies with \"Provided\" scope", FieldKind.Check)),
    JRE(ConfigurationField("JRE:", FieldKind.Combo, predicate = Predicate.startWith)),
    ShortenCmdLine(ConfigurationField("Shorten command line:", FieldKind.Combo,
                                      predicate = Predicate.startWith)),
    CapturingSnapshots(ConfigurationField("Enable capturing form snapshots", FieldKind.Check)),
    BeforeLaunch(ConfigurationField("Before launch:", FieldKind.Custom, custom = CustomConfigurationField.beforeLaunchField)),
  }

  enum class GlassfishFields(val conf: ConfigurationField) {
    AppServer(ConfigurationField("Application server:", FieldKind.Combo)),
    URL(ConfigurationField("URL:", FieldKind.Text)),
    AfterLaunchCheck(ConfigurationField("After launch", FieldKind.Check)),
    AfterLaunchCombo(ConfigurationField("After launch", FieldKind.Combo)),
    WithJavaScriptDebugger(ConfigurationField("with JavaScript debugger", FieldKind.Check)),
    VMOptions(ConfigurationField("VM options:", FieldKind.Text)),
    OnUpdateAction(ConfigurationField("On 'Update' action:", FieldKind.Combo)),
    ShowDialog(ConfigurationField("Show dialog", FieldKind.Check)),
    OnFrameDeactivation(ConfigurationField("On frame deactivation:", FieldKind.Combo)),
    JRE(ConfigurationField("JRE:", FieldKind.Combo, predicate = Predicate.startWith)),
    ServerDomain(ConfigurationField("Server Domain:", FieldKind.Combo)),
    Username(ConfigurationField("Username:", FieldKind.Text)),
    Password(ConfigurationField("Password:", FieldKind.Text)),
    PreserveSessions(ConfigurationField("Preserve Sessions Across Redeployment", FieldKind.Check)),
    JavaEE5Compat(ConfigurationField("Java EE 5 compatibility", FieldKind.Check)),
  }
}

val GuiTestCase.runConfigModel by RunConfigurationModel

fun RunConfigurationModel.connectDialog(): JDialogFixture =
  guiTestCase.dialog(RunConfigurationModel.Constants.runConfigTitle, true)

fun RunConfigurationModel.checkConfigurationExistsAndSelect(vararg configuration: String) {
  with(connectDialog()) {
    step("check configuration '${configuration.joinToString()}' exists") {
      assert(guiTestCase.exists { jTree(*configuration) }) { "Cannot find configuration '${configuration.joinToString()}'" }
      jTree(*configuration).clickPath()
    }
  }
}

fun RunConfigurationModel.closeWithCancel() {
  with(connectDialog()) {
    step("close dialog with 'Cancel' button") {
      button(RunConfigurationModel.Constants.buttonCancel).click()
    }
  }
}

fun RunConfigurationModel.closeWithOK() {
  with(connectDialog()) {
    step("close dialog with 'OK' button") {
      button(RunConfigurationModel.Constants.buttonOK).click()
    }
  }
}

fun RunConfigurationModel.checkOneValue(expectedField: RunConfigurationModel.ConfigurationField, expectedValue: String) {
  with(expectedField) {
    step("check field '$title'") {
    if (!isFieldPresent()) throw ComponentLookupException("Cannot find component with label `$title`")
    val actualValue = getFieldValue()
      logInfo("actual value = `$actualValue`, expected value = `$expectedValue`")
      assert(predicate(actualValue, expectedValue)) {
        "Field `$title`: actual value = `$actualValue`, expected value = `$expectedValue`"
      }
    }
  }
}

fun RunConfigurationModel.changeOneValue(expectedField: RunConfigurationModel.ConfigurationField, newValue: String) {
  with(expectedField) {
    step("change field `${expectedField.title}`to value = `$newValue`") {
      if (!isFieldPresent()) throw ComponentLookupException("Cannot find component with label `$title`")
      setFieldValue(newValue)
    }
  }
}

fun RunConfigurationModel.printHierarchy() {
  with(connectDialog()) {
    println(ScreenshotOnFailure.getHierarchy())
  }
}