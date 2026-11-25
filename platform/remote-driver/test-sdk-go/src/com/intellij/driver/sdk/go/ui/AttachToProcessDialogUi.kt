package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.table
import com.intellij.driver.sdk.ui.components.elements.textField

/** The "Attach to Process" dialog of the debugger. */
internal fun Finder.attachToProcessDialog(): AttachToProcessDialogUi =
  x(AttachToProcessDialogUi::class.java) { contains(byTitle(ATTACH_DIALOG_TITLE)) }

internal class AttachToProcessDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val filterField: JTextFieldUI =
    textField { and(byClass("TextFieldWithProcessing"), byAccessibleName("Search")) }

  val processTable: JTableUiComponent = table { byClass("AttachToProcessItemsList") }

  /**
   * The button which attaches the debugger to the selected process.
   *
   * The button is named after the debugger of the selection, so it is matched by its class.
   */
  val attachButton: UiComponent = x { byClass("JBOptionButton") }
}

/** The title also holds an ellipsis, so it is matched as a part of the title of the dialog. */
private const val ATTACH_DIALOG_TITLE = "Attach to Process"
