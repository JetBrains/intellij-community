// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorStripe
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorStripeSegment
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import git4idea.i18n.GitBundle
import git4idea.ui.branch.GitBranchReviewPresenter
import icons.CollaborationToolsIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.Border

internal class GitWorkingTreeRowComponent {
  val component: JComponent
    field = ListItemPanel()

  private val leadingIconLabel = JBLabel().apply { verticalAlignment = SwingConstants.TOP }
  private val nameLabel = createTextComponent()
  private val statusIconLabel = JBLabel()
  private val submoduleHintLabel = JBLabel(GitBundle.message("toolwindow.working.trees.worktree.kind.submodule.hint"))
  private val branchIconLabel = JBLabel(AllIcons.Vcs.Branch)
  private val branchLabel = createTextComponent().apply { minimumSize = Dimension(0, minimumSize.height) }
  private val prIconLabel = JBLabel()
  private val prTitleLabel = JBLabel().apply { minimumSize = Dimension(0, minimumSize.height) }

  init {
    component.isOpaque = true
    component.layout = BorderLayout(JBUI.scale(4), 0)
    component.selectionArc = SELECTION_ARC
    val firstLine = JPanel(ListLayout.horizontal(4)).apply {
      isOpaque = false
      add(nameLabel)
      add(submoduleHintLabel)
      add(statusIconLabel)
    }
    val secondLine = JPanel(ListLayout.horizontal(4)).apply {
      isOpaque = false
      add(branchIconLabel)
      add(branchLabel)
      add(prIconLabel)
      add(prTitleLabel)
    }
    val textLines = JPanel(VerticalLayout(JBUI.scale(2))).apply {
      isOpaque = false
      add(firstLine)
      add(secondLine)
    }
    component.add(leadingIconLabel, BorderLayout.WEST)
    component.add(textLines, BorderLayout.CENTER)
  }

  fun configure(
    row: GitWorkingTreesListEntry,
    selected: Boolean,
    hovered: Boolean,
    focused: Boolean,
    font: Font,
    color: Color?,
    part: RepositoryColorStripeSegment,
    review: GitBranchReviewPresenter.Review?,
    speedSearch: SpeedSearchSupply?,
  ) {
    val worktree = (row as? GitWorktreeRow)?.gitWorkingTree
    applySelectionColors(selected, hovered, focused, font, dimmed = row is GitWorktreeCreatingRow || worktree?.isPrunable == true)
    leadingIconLabel.icon = when {
      row is GitWorktreeCreatingRow -> AnimatedIcon.Default.INSTANCE
      worktree?.isCurrent == true -> AllIcons.Actions.Checked
      else -> AllIcons.Empty
    }
    val nameStyle = if (worktree?.isMain == true) SimpleTextAttributes.STYLE_BOLD else SimpleTextAttributes.STYLE_PLAIN
    nameLabel.clear()
    nameLabel.append(row.presentableName, SimpleTextAttributes(nameStyle, null))
    statusIconLabel.icon = worktree?.let { statusIcon(it.isLocked) }
    submoduleHintLabel.isVisible = row is GitWorktreeRow && row.repositoryKind == GitRepositoryKind.SUBMODULE
    branchLabel.clear()
    branchLabel.append(row.presentableBranchName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    prIconLabel.icon = if (review != null) IconUtil.colorize(CollaborationToolsIcons.PullRequestOpen, prTitleLabel.foreground) else null
    prIconLabel.isVisible = review != null
    prTitleLabel.text = review?.title
    prTitleLabel.isVisible = review != null
    if (speedSearch != null) {
      SpeedSearchUtil.applySpeedSearchHighlighting(speedSearch, nameLabel, false, selected)
      SpeedSearchUtil.applySpeedSearchHighlighting(speedSearch, branchLabel, false, selected)
    }
    setStripe(color, part)
    component.setToolTipText(row.tooltipText())
    component.accessibleContext.accessibleName = AccessibleContextUtil.getCombinedName(", ", nameLabel, branchLabel, prTitleLabel)
  }

  private fun createTextComponent(): SimpleColoredComponent = SimpleColoredComponent().apply {
    isOpaque = false
    ipad = JBInsets.emptyInsets()
    myBorder = null
  }

  private fun setStripe(color: Color?, part: RepositoryColorStripeSegment) {
    val insets = if (color != null) STRIPED_INSETS else PLAIN_INSETS
    component.border = StripeBorder(color, part, insets)
    component.selectionInsets =
      if (color != null) JBUI.insetsLeft(RepositoryColorStripe.LEFT_GAP + RepositoryColorStripe.WIDTH + STRIPE_RIGHT_GAP)
      else JBUI.emptyInsets()
  }

  // A row in the worktrees list is a compound cell, not a plain panel; JList's accessibility bridge otherwise reports it as PANEL.
  private class ListItemPanel : SelectablePanel() {
    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleJPanel() {
          override fun getAccessibleRole() = AccessibleRole.LIST_ITEM
        }
      }
      return accessibleContext
    }
  }

  private class StripeBorder(
    private val color: Color?,
    private val segment: RepositoryColorStripeSegment,
    private val insets: Insets,
  ) : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val stripeColor = color ?: return
      val g2 = g.create() as Graphics2D
      try {
        RepositoryColorStripe.paintSegment(g2, stripeColor, height, segment)
      }
      finally {
        g2.dispose()
      }
    }

    override fun getBorderInsets(c: Component): Insets = insets
    override fun isBorderOpaque(): Boolean = false
  }

  private fun applySelectionColors(selected: Boolean, hovered: Boolean, focused: Boolean, font: Font, dimmed: Boolean) {
    component.background = null
    component.selectionColor = when {
      selected -> UIUtil.getListBackground(true, focused)
      hovered -> JBUI.CurrentTheme.List.Hover.background(focused)
      else -> null
    }
    val primaryForeground = UIUtil.getListForeground(selected, focused)
    if (dimmed) {
      val disabledForeground = JBUI.CurrentTheme.Label.disabledForeground(selected)
      nameLabel.foreground = disabledForeground
      branchLabel.foreground = disabledForeground
      prTitleLabel.foreground = disabledForeground
    }
    else {
      nameLabel.foreground = primaryForeground
      branchLabel.foreground = if (selected) primaryForeground else NamedColorUtil.getInactiveTextColor()
      prTitleLabel.foreground = if (selected) primaryForeground else NamedColorUtil.getInactiveTextColor()
    }
    statusIconLabel.foreground = primaryForeground
    submoduleHintLabel.foreground = if (selected) primaryForeground else NamedColorUtil.getInactiveTextColor()

    component.font = font
    nameLabel.font = font
    submoduleHintLabel.font = font
    branchLabel.font = font
    prTitleLabel.font = font
  }

  private fun statusIcon(locked: Boolean): Icon? = if (locked) LOCKED_ICON else null

  companion object {
    private val LOCKED_ICON = AllIcons.Ide.Readonly
    private val SELECTION_ARC get() = JBUI.CurrentTheme.Popup.Selection.ARC.get()
    private val STRIPE_RIGHT_GAP get() = JBUI.scale(4)

    private val PLAIN_INSETS get() = JBUI.insets(4, 8)
    private val STRIPED_INSETS
      get() = JBUI.insets(4, 8 + RepositoryColorStripe.LEFT_GAP + RepositoryColorStripe.WIDTH + STRIPE_RIGHT_GAP, 4, 8)
  }
}
