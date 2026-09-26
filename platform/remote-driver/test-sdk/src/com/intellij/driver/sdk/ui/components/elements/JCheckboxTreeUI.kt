package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.model.TreePathToRowListWithCheckboxStateList
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import java.awt.Rectangle
import javax.swing.JCheckBox
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

fun Finder.checkBoxTree(locator: QueryBuilder.() -> String = { byType("com.intellij.ui.CheckboxTreeBase") }): JCheckboxTreeFixture =
  x(JCheckboxTreeFixture::class.java, locator)

class JCheckboxTreeFixture(data: ComponentData) : JTreeUiComponent(data) {
  val treeFixture = driver.new(JCheckboxTreeUIRef::class, robot, component)

  fun switchCheckBoxByPath(path: List<String>, state: Boolean) {
    treeFixture.switchCheckBoxByPath(path, state)
  }

  fun clickOnCheckbox(checkbox: JCheckBox, fileTrePathLocation: Rectangle) {
    treeFixture.clickOnCheckbox(checkbox, fileTrePathLocation)
  }

  fun getCheckBoxForNode(node: DefaultMutableTreeNode?, fileTreePath: TreePath): String {
    return treeFixture.getCheckBoxForNode(node, fileTreePath)
  }

  fun collectCheckboxes(): TreePathToRowListWithCheckboxStateList {
    return treeFixture.collectCheckboxes()
  }

}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JCheckboxTreeFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JCheckboxTreeUIRef : JTreeFixtureRef {
  fun collectCheckboxes(): TreePathToRowListWithCheckboxStateList
  fun switchCheckBoxByPath(path: List<String>, state: Boolean)
  fun clickOnCheckbox(checkbox: JCheckBox, fileTrePathLocation: Rectangle)
  fun getCheckBoxForNode(node: DefaultMutableTreeNode?, fileTreePath: TreePath): String
}
