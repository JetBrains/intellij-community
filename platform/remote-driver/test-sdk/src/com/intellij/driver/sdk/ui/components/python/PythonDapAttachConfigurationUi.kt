package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.common.dialogs.EditRunConfigurationsDialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.textField

/** The "Remote address" field of the "Attach to DAP" configuration editor in the Run/Debug Configurations dialog. */
fun EditRunConfigurationsDialogUiComponent.dapRemoteAddressField(): JTextFieldUI =
  textField("'Remote address' field of 'Attach to DAP'") { and(byClass("JBTextField"), byAccessibleName("Remote address:")) }
