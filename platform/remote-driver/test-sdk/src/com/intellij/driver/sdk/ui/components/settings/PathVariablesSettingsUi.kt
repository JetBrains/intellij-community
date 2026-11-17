package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.fileChooser
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.textFieldWithBrowseButton
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.ui
import java.io.File
import java.nio.file.Path
import javax.swing.JTextField

/**
 * Settings | Appearance & Behavior | Path Variables page, see `com.intellij.application.options.pathMacros.PathMacroConfigurable`.
 * The page has no class of its own, so the lookup takes the settings content with the table of the page.
 */
fun SettingsDialogUiComponent.pathVariablesSettingsPage(action: PathVariablesSettingsPageUi.() -> Unit = {}): PathVariablesSettingsPageUi =
  x(PathVariablesSettingsPageUi::class.java, "Path Variables settings page") {
    componentWithChild(byType("com.intellij.openapi.options.ex.ConfigurableCardPanel"),
                       byType("com.intellij.application.options.pathMacros.PathMacroTable"))
  }.apply(action)

class PathVariablesSettingsPageUi(data: ComponentData) : UiComponent(data) {
  /**
   * The cells are read by the accessible name of the renderer, that is the full text of the cell.
   * The default reader takes the painted text, and the table cuts a long path with '...'.
   */
  val variablesTable: JTableUiComponent = accessibleTable { byType("com.intellij.application.options.pathMacros.PathMacroTable") }

  /** The values of the variables in the table by their names. The columns of the table are Name and Value. */
  val variables: Map<String, String?>
    get() = variablesTable.content().values.mapNotNull { row -> row[0]?.let { it to row[1] } }.toMap()

  val ignoredVariablesField: JTextFieldUI = fieldLabeledBy("Ignored Variables")

  val addButton: UiComponent = toolbarButton("Add")
  val editButton: UiComponent = toolbarButton("Edit")
  val removeButton: UiComponent = toolbarButton("Remove")

  /** The change is applied by OK or Apply of Settings. */
  fun addVariable(name: String, value: String) {
    addButton.click()
    fillVariableDialog("Add Variable", name, value)
  }

  /**
   * Adds the variable with the [name] and takes its value from the file chooser opened by the Browse button of the value field.
   * The change is applied by OK or Apply of Settings.
   */
  fun addVariableByBrowse(name: String, folder: Path) {
    addButton.click()
    driver.ui.dialog(title = "Add Variable") {
      fieldLabeledBy("Name").text = name
      textFieldWithBrowseButton().browseButton.click()
      driver.ui.fileChooser({ byTitle("Select Path") }).openPath(folder)
      // Without the path the OK button is disabled, and the dialog stays open with no hint why.
      textFieldWithBrowseButton().should("The value field should have the path of '$folder'") {
        File(text).canonicalPath == File(folder.toString()).canonicalPath
      }
      okButton.click()
      waitNotFound()
    }
  }

  /**
   * Renames the variable with the [name] to [newName] and sets its [value].
   * A `null` [newName] keeps the name. The change is applied by OK or Apply of Settings.
   */
  fun editVariable(name: String, value: String, newName: String? = null) {
    variablesTable.clickCell { it == name }
    editButton.click()
    fillVariableDialog("Edit Variable", newName, value)
  }

  /** The change is applied by OK or Apply of Settings. */
  fun removeVariable(name: String) {
    variablesTable.clickCell { it == name }
    removeButton.click()
  }

  /**
   * The fields have no accessible name of their own, they inherit it from the label bound with `JLabel.setLabelFor`,
   * so the trailing colon of the label may or may not be a part of it.
   */
  private fun UiComponent.fieldLabeledBy(label: String): JTextFieldUI =
    textField("'$label' field") {
      and(byType(JTextField::class.java), or(byAccessibleName(label), byAccessibleName("$label:")))
    }

  private fun toolbarButton(name: String): UiComponent =
    x("'$name' button") { and(byType("com.intellij.openapi.actionSystem.impl.ActionButton"), byAccessibleName(name)) }

  /** Fills in the dialog with the [title] and closes it by OK. A `null` [name] keeps the name of the variable. */
  private fun fillVariableDialog(title: String, name: String?, value: String) {
    driver.ui.dialog(title = title) {
      if (name != null) {
        fieldLabeledBy("Name").text = name
      }
      textFieldWithBrowseButton().text = value
      okButton.click()
      waitNotFound()
    }
  }
}
