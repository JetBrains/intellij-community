// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckboxTreeBase.CheckboxTreeCellRendererBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.ui.ThreeStateCheckBox
import org.junit.Assert
import javax.swing.tree.DefaultTreeModel

/**
 * Tests for [CheckboxTreeBase] functionality.
 */
class CheckboxTreeBaseTest : LightPlatformTestCase() {
  override fun runInDispatchThread(): Boolean {
    return true
  }

  /**
   * Creates a tree structure with a parent node and three children.
   * 
   * @param parentChecked Whether the parent node should be checked
   * @param childrenChecked List of boolean values indicating whether each child should be checked
   * @return Pair of the root node and the parent node
   */
  private fun createTreeStructure(parentChecked: Boolean, childrenChecked: List<Boolean>): Pair<CheckedTreeNode, CheckedTreeNode> {
    val root = CheckedTreeNode("Root")
    val parent = CheckedTreeNode("Parent")
    parent.setChecked(parentChecked)
    
    for ((i, checked) in childrenChecked.withIndex()) {
      val child = CheckedTreeNode("Child $i")
      child.setChecked(checked)
      parent.add(child)
    }
    
    root.add(parent)
    return Pair(root, parent)
  }
  
  /**
   * Gets the checkbox state for a node in the tree.
   * 
   * @param root The root node of the tree
   * @param node The node to check the state for
   * @return The state of the checkbox for the node
   */
  private fun getNodeCheckboxState(root: CheckedTreeNode, node: CheckedTreeNode): ThreeStateCheckBox.State {
    val treeModel = DefaultTreeModel(root)
    val tree = CheckboxTreeBase(CheckboxTreeCellRendererBase(), root)
    tree.setModel(treeModel)
    
    val renderer = tree.getCellRenderer() as CheckboxTreeCellRendererBase
    renderer.getTreeCellRendererComponent(tree, node, false, true, false, 1, false)
    
    return renderer.myCheckbox.state
  }
  /**
   * Tests that when all children have the same state, but it differs from the parent's state,
   * the parent node shows a partial state (DONT_CARE).
   */
  fun testParentNodeStateWithAllChildrenDifferent() {
    // Create a tree with a parent node that is checked and children that are all unchecked
    val (root, parent) = createTreeStructure(true, listOf(false, false, false))
    
    val state = getNodeCheckboxState(root, parent)
    
    // The parent node should have a partial state (DONT_CARE) because all children are unchecked
    // but the parent itself is checked
    Assert.assertEquals("Parent node should have a partial state when all children have a different state",
                        ThreeStateCheckBox.State.DONT_CARE, state)
  }

  /**
   * Tests that when all children have the same state as the parent,
   * the parent node shows the same state.
   */
  fun testParentNodeStateWithAllChildrenSame() {
    // Create a tree with a parent node that is checked and children that are all checked
    val (root, parent) = createTreeStructure(true, listOf(true, true, true))
    
    val state = getNodeCheckboxState(root, parent)
    
    // The parent node should have the same state as its children (SELECTED)
    Assert.assertEquals("Parent node should have the same state as its children",
                        ThreeStateCheckBox.State.SELECTED, state)
  }

  /**
   * Tests that when children have mixed states,
   * the parent node shows a partial state (DONT_CARE).
   */
  fun testParentNodeStateWithMixedChildren() {
    // Create a tree with a parent node that is checked and children with mixed states
    val (root, parent) = createTreeStructure(true, listOf(true, false, true))
    
    val state = getNodeCheckboxState(root, parent)
    
    // The parent node should have a partial state (DONT_CARE) because children have mixed states
    Assert.assertEquals("Parent node should have a partial state when children have mixed states",
                        ThreeStateCheckBox.State.DONT_CARE, state)
  }
}