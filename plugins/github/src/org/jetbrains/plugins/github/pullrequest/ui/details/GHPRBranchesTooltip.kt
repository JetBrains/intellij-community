// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.roots.ui.componentsList.layout.VerticalStackLayout
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.vcs.log.ui.render.LabelIcon
import com.intellij.vcs.log.ui.render.LabelPainter
import git4idea.GitUtil.HEAD
import git4idea.log.GitRefManager
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Font
import javax.swing.Icon
import javax.swing.SwingConstants

internal class GHPRBranchesTooltip(private val descriptors: List<BranchTooltipDescriptor> = emptyList()) :
  NonOpaquePanel(VerticalStackLayout()) {

  init {
    update()
  }

  fun update() {
    removeAll()
    val height: Int = getIconHeight()

    for (descriptor in descriptors) {
      val icon: Icon = createIcon(descriptor, height)
      val text = descriptor.name
      val label: JBLabel = createLabel(text, icon)
      add(label)
    }

    isVisible = descriptors.isNotEmpty()
    revalidate()
    repaint()
  }

  private fun getIconHeight(): Int = getFontMetrics(getLabelsFont()).height

  private fun getLabelsFont(): Font = LabelPainter.getReferenceFont()

  private fun createIcon(descriptor: BranchTooltipDescriptor, height: Int): Icon {
    return LabelIcon(this, height, background, listOf(descriptor.color))
  }

  private fun createLabel(@Nls text: String, icon: Icon?): JBLabel {
    val label = JBLabel(text, icon, SwingConstants.LEFT)
    label.font = getLabelsFont()
    label.iconTextGap = JBUIScale.scale(4)
    label.verticalTextPosition = SwingConstants.CENTER
    return label
  }

}

internal data class BranchTooltipDescriptor(@NlsSafe val name: String, val color: Color) {
  companion object {
    private val PR_BRANCH_COLOR = JBColor(Color(0xf6e6e6e), Color(0xfafb1b3))

    fun head() = BranchTooltipDescriptor(HEAD, GitRefManager.HEAD.backgroundColor)
    fun localBranch(branchName: String) = BranchTooltipDescriptor(branchName, GitRefManager.LOCAL_BRANCH.backgroundColor)
    fun remoteBranch(branchName: String) = BranchTooltipDescriptor(branchName, GitRefManager.REMOTE_BRANCH.backgroundColor)
    fun prBranch(branchName: String) = BranchTooltipDescriptor(branchName, PR_BRANCH_COLOR)
  }
}
