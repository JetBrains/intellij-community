// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Key
import com.intellij.ui.*
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultControl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import git4idea.ui.branch.popup.GitBranchesTreePopupStep
import java.awt.Component
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.accessibility.AccessibleContext
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

class GitBranchesTreeRenderer(private val step: GitBranchesTreePopupStep) : TreeCellRenderer {

  fun getLeftTreeIconRenderer(path: TreePath): Control? {
    val lastComponent = path.lastPathComponent
    val defaultIcon = step.getNodeIcon(lastComponent, false) ?: return null
    val selectedIcon = step.getNodeIcon(lastComponent, true) ?: return null

    return DefaultControl(defaultIcon, defaultIcon, selectedIcon, selectedIcon)
  }

  private val mainIconComponent = JLabel().apply {
    ClientProperty.put(this, MAIN_ICON, true)
    border = JBUI.Borders.emptyRight(4)  // 6 px in spec, but label width is differed
  }
  private val mainTextComponent = SimpleColoredComponent().apply {
    isOpaque = false
    border = JBUI.Borders.empty()
  }
  private val secondaryLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(10)
    horizontalAlignment = SwingConstants.RIGHT
  }
  private val arrowLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(4) // 6 px in spec, but label width is differed
  }
  private val incomingOutgoingLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(10)
  }

  private val branchInfoPanel = JBUI.Panels.simplePanel(mainTextComponent)
    .addToLeft(mainIconComponent)
    .addToRight(incomingOutgoingLabel)
    .andTransparent()

  private val textPanel = JBUI.Panels.simplePanel()
    .addToCenter(JPanel(GridBagLayout()).apply {
      isOpaque = false

      add(branchInfoPanel,
          GridBagConstraints().apply {
            anchor = GridBagConstraints.LINE_START
            weightx = 1.0
          })

      add(secondaryLabel,
          GridBagConstraints().apply {
            anchor = GridBagConstraints.LINE_END
            weightx = 2.0
          })
    })
    .andTransparent()

  private inner class MyMainPanel : BorderLayoutPanel() {
    init {
      addToCenter(textPanel)
      addToRight(arrowLabel)
      andTransparent()
      withBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.cellPadding().right))
    }

    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleContextDelegateWithContextMenu(mainTextComponent.accessibleContext) {
          override fun getDelegateParent(): Container = parent

          override fun doShowContextMenu() {
            ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true)
          }
        }
      }
      return accessibleContext
    }
  }

  private val mainPanel = MyMainPanel()

  override fun getTreeCellRendererComponent(tree: JTree?,
                                            value: Any?,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): Component? {
    val userObject = TreeUtil.getUserObject(value)
    // render separator text in accessible mode
    if (userObject is SeparatorWithText) return if (userObject.caption != null) userObject else null

    mainIconComponent.apply {
      icon = step.getIcon(userObject, selected)
      isVisible = icon != null
    }

    mainTextComponent.apply {
      background = JBUI.CurrentTheme.Tree.background(selected, true)
      foreground = JBUI.CurrentTheme.Tree.foreground(selected, true)

      clear()
      append(step.getText(userObject).orEmpty())
    }

    val (inOutIcon, inOutTooltip) = step.getIncomingOutgoingIconWithTooltip(userObject)
    tree?.toolTipText = inOutTooltip

    incomingOutgoingLabel.apply {
      icon = inOutIcon
      isVisible = icon != null
    }

    arrowLabel.apply {
      isVisible = step.hasSubstep(userObject)
      icon = if (selected) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow
    }

    secondaryLabel.apply {
      text = step.getSecondaryText(userObject)
      //todo: LAF color
      foreground = if (selected) JBUI.CurrentTheme.Tree.foreground(true, true) else JBColor.GRAY

      border = if (!arrowLabel.isVisible && ExperimentalUI.isNewUI()) {
        JBUI.Borders.empty(0, 10, 0, JBUI.CurrentTheme.Popup.Selection.innerInsets().right)
      }
      else {
        JBUI.Borders.emptyLeft(10)
      }
    }

    if (tree != null && value != null) {
      SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, mainTextComponent, true, selected)
    }

    return mainPanel
  }

  companion object {
    @JvmField
    internal val MAIN_ICON = Key.create<Boolean>("MAIN_ICON")
  }
}
