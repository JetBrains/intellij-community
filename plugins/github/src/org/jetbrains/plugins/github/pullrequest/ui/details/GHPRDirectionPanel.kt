// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.FlowLayout
import javax.swing.JLabel

internal class GHPRDirectionPanel : NonOpaquePanel(FlowLayout(FlowLayout.LEFT, 0, UIUtil.DEFAULT_VGAP)) {
  private val from = createLabel()
  private val to = createLabel()

  var direction: Pair<String, String>?
    by equalVetoingObservable<Pair<String, String>?>(null) {
      from.text = " ${it?.first} "
      to.text = " ${it?.second} "
      this@GHPRDirectionPanel.isVisible = it != null
    }

  init {
    add(from)
    add(JLabel(" ${UIUtil.rightArrow()} ").apply {
      foreground = CurrentBranchComponent.TEXT_COLOR
      border = JBUI.Borders.empty(0, 5)
    })
    add(to)
  }

  companion object {
    private fun createLabel() = object : JBLabel(UIUtil.ComponentStyle.REGULAR) {
      init {
        updateColors()
      }

      override fun updateUI() {
        super.updateUI()
        updateColors()
      }

      private fun updateColors() {
        foreground = CurrentBranchComponent.TEXT_COLOR
        background = CurrentBranchComponent.getBranchPresentationBackground(UIUtil.getListBackground())
      }
    }.andOpaque()
  }
}