// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.logical.model.LogicalModelPresentationProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

internal class LogicalStructureNodeRenderer: TreeCellRenderer, StructureViewModel.ElementRendererProvider {

  private val defaultRenderer = NodeRenderer()

  override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
    val treeElement = ((value as? DefaultMutableTreeNode)?.userObject as? AbstractTreeNode<*>)?.value as? StructureViewTreeElement
                      ?: return defaultRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    val model = if (treeElement is LogicalStructureViewTreeElement<*>) {
      treeElement.getLogicalAssembledModel().model
    }
    else {
      treeElement.value
    }
    if (model == null) {
      return defaultRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    }
    val presentationProvider = LogicalModelPresentationProvider.getForObject(model)
    //if (presentationProvider == null) {
      return defaultRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    //}
    //return wrap(NodeRenderer().getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus), model, presentationProvider)
  }

  private fun wrap(component: Component, model: Any, presentationProvider: LogicalModelPresentationProvider<Any>): Component {
    val result = BorderLayoutPanel()
    result.withBorder(JBUI.Borders.emptyRight(10))
    result.addToCenter(component)
    // TODO StructureTW: icons from presentationProvider
    val buttonPanel = JPanel(HorizontalLayout(0, SwingConstants.CENTER))
    buttonPanel.isEnabled = false
    buttonPanel.add(IconButton(AllIcons.General.Note))
    buttonPanel.add(IconButton(AllIcons.RunConfigurations.TestState.Run))
    result.addToRight(buttonPanel)
    return result
  }

  override fun getRenderer(): TreeCellRenderer = this

  override fun handleClick(dx: Int, treeElement: StructureViewTreeElement, componentSupplier: Supplier<Component>?): Boolean {
    //((SimpleColoredComponent) ((BorderLayoutPanel)component).getComponent(0)).getFragmentTag(2)
    return false
  }

  private fun getButtonsPanel(componentSupplier: Supplier<Component>?): JPanel? {
    val component = componentSupplier?.get() as? BorderLayoutPanel ?: return null
    return component.getComponent(1) as? JPanel
  }

}

private class IconButton(defaultIcon: Icon) : JButton(defaultIcon) {
  private var myDefaultIcon: Icon?

  init {
    val size: Dimension = JBDimension(22, 22)

    border = null
    isContentAreaFilled = false
    maximumSize = size
    minimumSize = size
    preferredSize = size

    myDefaultIcon = defaultIcon
  }

}