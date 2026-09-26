// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object VcsConfirmationUtil {

  @JvmStatic
  @JvmOverloads
  fun requestConfirmation(
    option: VcsShowConfirmationOption,
    project: Project,
    message: @NlsContexts.DialogMessage String,
    title: @NlsContexts.DialogTitle String,
    icon: Icon?,
    okActionName: @NlsActions.ActionText String? = null,
    cancelActionName: @NlsActions.ActionText String? = null,
  ): Boolean {
    if (option.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return false

    val doNotAskOption = VcsDoNotAskOption(option)
    val defaultAction = okActionName ?: Messages.getYesButton()
    val cancelAction = cancelActionName ?: Messages.getNoButton()

    return MessageDialogBuilder.Message(title, message)
      .icon(icon)
      .buttons(defaultAction, cancelAction)
      .focusedButton(defaultAction)
      .defaultButton(defaultAction)
      .doNotAsk(doNotAskOption)
      .show(project) == defaultAction
  }
}

private class VcsDoNotAskOption(val vcsOption: VcsShowConfirmationOption) : DoNotAskOption {
  override fun isToBeShown(): Boolean = vcsOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION

  override fun setToBeShown(value: Boolean, exitCode: Int) {
    if (!vcsOption.isPersistent()) return

    if (value) {
      vcsOption.setValue(VcsShowConfirmationOption.Value.SHOW_CONFIRMATION)
      return
    }

    val newValue = if (exitCode == Messages.YES) VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
    else VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY

    vcsOption.setValue(newValue)

  }

  override fun canBeHidden(): Boolean = vcsOption.isPersistent()
  override fun shouldSaveOptionsOnCancel(): Boolean = true
  override fun getDoNotShowMessage(): String = IdeCoreBundle.message("dialog.options.do.not.ask")
}
