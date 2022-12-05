// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.AmendCommitHandler
import com.intellij.vcs.commit.AmendCommitModeListener
import com.intellij.vcs.commit.ToggleAmendCommitOption
import com.intellij.xml.util.XmlStringUtil
import org.zmlx.hg4idea.HgBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Commit options for hg
 */
open class HgCommitAdditionalComponent(private val myCommitPanel: CheckinProjectPanel,
                                       private val myCommitContext: CommitContext,
                                       hasSubrepos: Boolean,
                                       showAmendOption: Boolean) : RefreshableOnComponent, AmendCommitModeListener, Disposable {
  private val myPanel: JPanel
  private val myCommitSubrepos: JCheckBox
  private val myAmendOption: ToggleAmendCommitOption?

  init {
    myAmendOption = if (showAmendOption) ToggleAmendCommitOption(myCommitPanel, this) else null

    myCommitSubrepos = JCheckBox(HgBundle.message("repositories.commit.subs"), false)
    myCommitSubrepos.isVisible = hasSubrepos
    myCommitSubrepos.toolTipText = XmlStringUtil.wrapInHtml(HgBundle.message("repositories.commit.subs.tooltip"))
    myCommitSubrepos.addActionListener { e: ActionEvent? -> updateAmendState(!myCommitSubrepos.isSelected) }

    val gb = GridBag()
      .setDefaultInsets(JBUI.insets(2))
      .setDefaultAnchor(GridBagConstraints.WEST)
      .setDefaultWeightX(1.0)
      .setDefaultFill(GridBagConstraints.HORIZONTAL)
    myPanel = JPanel(GridBagLayout())
    if (myAmendOption != null) myPanel.add(myAmendOption, gb.nextLine().next())
    myPanel.add(myCommitSubrepos, gb.nextLine().next())

    amendHandler.addAmendCommitModeListener(this, this)
  }

  private val amendHandler: AmendCommitHandler
    get() {
      return myCommitPanel.commitWorkflowHandler.amendCommitHandler;
    }

  override fun dispose() {}

  override fun amendCommitModeToggled() {
    updateCommitSubreposState()
  }

  override fun saveState() {
    myCommitContext.isCommitSubrepositories = myCommitSubrepos.isSelected
  }

  override fun restoreState() {
    updateCommitSubreposState()
  }

  override fun getComponent(): JComponent {
    return myPanel
  }

  fun isAmend(): Boolean {
    return amendHandler.isAmendCommitMode
  }

  private fun updateCommitSubreposState() {
    val isAmendMode = isAmend()

    myCommitSubrepos.isEnabled = !isAmendMode
    if (isAmendMode) myCommitSubrepos.isSelected = false
  }

  private fun updateAmendState(enable: Boolean) {
    amendHandler.isAmendCommitModeTogglingEnabled = enable
    if (myAmendOption != null) myAmendOption.isEnabled = enable
    if (!enable) amendHandler.isAmendCommitMode = false
  }
}