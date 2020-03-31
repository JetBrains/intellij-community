// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent

@ApiStatus.Internal
class ToggleAmendCommitOption(commitPanel: CheckinProjectPanel, parent: Disposable) : JBCheckBox("Amend commit") {
  private val amendCommitHandler = commitPanel.commitWorkflowHandler.amendCommitHandler

  init {
    isFocusable = UISettings.shadowInstance.disableMnemonicsInControls
    mnemonic = KeyEvent.VK_M
    toolTipText = "Merge this commit with the previous one"

    addActionListener { amendCommitHandler.isAmendCommitMode = isSelected }
    amendCommitHandler.addAmendCommitModeListener(object : AmendCommitModeListener {
      override fun amendCommitModeToggled() {
        isSelected = amendCommitHandler.isAmendCommitMode
      }
    }, parent)
  }

  companion object {
    @JvmStatic
    fun isAmendCommitOptionSupported(commitPanel: CheckinProjectPanel, amendAware: AmendCommitAware) =
      !commitPanel.isNonModalCommit && amendAware.isAmendCommitSupported()
  }
}