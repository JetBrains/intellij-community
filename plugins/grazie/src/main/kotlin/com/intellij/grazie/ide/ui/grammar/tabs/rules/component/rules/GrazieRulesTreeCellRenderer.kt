// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules

import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.GrazieTreeComponent
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ui.CheckboxTree
import com.intellij.util.ui.UIUtil
import javax.swing.JTree

class GrazieRulesTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer(true) {
  override fun customizeRenderer(tree: JTree,
                                 node: Any,
                                 selected: Boolean,
                                 expanded: Boolean,
                                 leaf: Boolean,
                                 row: Int,
                                 hasFocus: Boolean) {
    if (node !is GrazieRulesTreeNode || tree !is GrazieTreeComponent || !tree.isShowing) return

    val background = UIUtil.getTreeBackground(selected, true)
    UIUtil.changeBackGround(this, background)

    SearchUtil.appendFragments(tree.getCurrentFilterString(), node.nodeText, node.attrs.style, node.attrs.fgColor, background, textRenderer)
  }
}
