// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import javax.swing.JLabel

internal class GHPRDirectionPanel : NonOpaquePanel() {
  private val from = createLabel()
  private val to = createLabel()
  private val checkoutLink = LinkLabel<Any>(VcsBundle.message("vcs.command.name.checkout"), null) { _, _ ->
    val action = ActionManager.getInstance().getAction("Github.PullRequest.Branch.Create")
    ActionUtil.invokeAction(action, this, ActionPlaces.UNKNOWN, null, null)
  }.apply {
    border = JBUI.Borders.emptyLeft(8)
  }

  var direction: Pair<String, String>?
    by equalVetoingObservable<Pair<String, String>?>(null) {
      from.text = "${it?.first} "
      to.text = "${it?.second} "
      this@GHPRDirectionPanel.isVisible = it != null
    }

  init {
    layout = MigLayout(LC()
                         .fillX()
                         .gridGap("0", "0")
                         .insets("0", "0", "0", "0"))

    add(to, CC().minWidth("${UI.scale(30)}"))
    add(JLabel(" ${UIUtil.leftArrow()} ").apply {
      foreground = CurrentBranchComponent.TEXT_COLOR
      border = JBUI.Borders.empty(0, 5)
    })
    add(from, CC().minWidth("${UI.scale(30)}"))
    add(checkoutLink)
  }

  companion object {
    private fun createLabel() = JBLabel(GithubIcons.Branch).also {
      GithubUIUtil.overrideUIDependentProperty(it) {
        foreground = CurrentBranchComponent.TEXT_COLOR
        background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())
      }
    }.andOpaque()
  }
}