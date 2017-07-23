/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.cellReader

import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import org.fest.swing.cell.JTreeCellReader
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * @author Sergey Karashevich
 */
class SettingsTreeCellReader : JTreeCellReader {

  override fun valueAt(tree: JTree, treeNode: Any?): String? {
    return getValueFromNode(tree, treeNode!!)
  }

  fun getValueFromNode(jTree: JTree, treeNode: Any): String? {
    if (treeNode !is DefaultMutableTreeNode) return null
    val userObject = treeNode.userObject as? FilteringTreeStructure.FilteringNode ?: return null
    val delegate = userObject.delegate as? CachingSimpleNode ?: return null
    return delegate.toString()
  }
}
