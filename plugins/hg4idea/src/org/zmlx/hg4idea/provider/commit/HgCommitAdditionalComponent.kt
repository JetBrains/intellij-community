// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.vcs.commit.AmendCommitModeListener
import com.intellij.vcs.commit.ToggleAmendCommitOption
import com.intellij.xml.util.XmlStringUtil
import org.zmlx.hg4idea.HgBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * Commit options for hg
 */
open class HgCommitAdditionalComponent(private val myCommitPanel: CheckinProjectPanel,
                                       private val myCommitContext: CommitContext,
                                       hasSubrepos: Boolean,
                                       showAmendOption: Boolean) : RefreshableOnComponent, AmendCommitModeListener, Disposable {
  private val amendHandler = myCommitPanel.commitWorkflowHandler.amendCommitHandler

  private val commitSubrepos: JCheckBox?
  private val amendOption: ToggleAmendCommitOption?

  init {
    commitSubrepos = when {
      hasSubrepos -> {
        JCheckBox(HgBundle.message("repositories.commit.subs"), false).also {
          it.toolTipText = XmlStringUtil.wrapInHtml(HgBundle.message("repositories.commit.subs.tooltip"))
          it.addActionListener { e -> updateAmendState(!it.isSelected) }
        }
      }
      else -> null
    }

    amendOption = when {
      showAmendOption -> ToggleAmendCommitOption(myCommitPanel, this)
      else -> null
    }

    amendHandler.addAmendCommitModeListener(this, this)
  }

  override fun getComponent(): JComponent {
    return panel {
      if (amendOption != null) {
        row {
          cell(amendOption)
        }
      }
      if (commitSubrepos != null) {
        row {
          cell(commitSubrepos)
        }
      }
    }
  }

  override fun dispose() {}

  override fun amendCommitModeToggled() {
    updateCommitSubreposState()
  }

  override fun saveState() {
    if (commitSubrepos != null) {
      myCommitContext.isCommitSubrepositories = commitSubrepos.isSelected
    }
  }

  override fun restoreState() {
    updateCommitSubreposState()
  }

  fun isAmend(): Boolean = amendHandler.isAmendCommitMode

  private fun updateCommitSubreposState() {
    if (commitSubrepos != null) {
      val isAmendMode = isAmend()

      commitSubrepos.isEnabled = !isAmendMode
      if (isAmendMode) commitSubrepos.isSelected = false
    }
  }

  private fun updateAmendState(enable: Boolean) {
    amendHandler.isAmendCommitModeTogglingEnabled = enable
    if (amendOption != null) amendOption.isEnabled = enable
    if (!enable) amendHandler.isAmendCommitMode = false
  }
}