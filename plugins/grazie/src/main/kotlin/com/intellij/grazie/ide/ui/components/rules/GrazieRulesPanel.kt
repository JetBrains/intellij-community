// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.rules

import com.intellij.grazie.ide.ui.components.dsl.actionGroup
import com.intellij.grazie.ide.ui.components.dsl.panel
import com.intellij.grazie.jlanguage.Lang
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode

class GrazieRulesPanel(onSelectionChanged: (meta: Any) -> Unit) : Disposable {
  @Suppress("UNNECESSARY_SAFE_CALL")
  private val tree: GrazieRulesTree = GrazieRulesTree(GrazieCheckboxTreeCellRenderer { filter?.filter ?: "" })
  private val filter: GrazieFilterComponent = GrazieFilterComponent(tree, "GRAZIE_RULES_FILTER", "GRAZIE_RULES_SEARCH")

  init {
    tree.selectionModel.addTreeSelectionListener { event ->
      val meta = (event?.path?.lastPathComponent as DefaultMutableTreeNode).userObject
      if (meta != null) onSelectionChanged(meta)
    }
  }

  val panel by lazy {
    panel {
      panel(constraint = BorderLayout.NORTH) {
        border = JBUI.Borders.emptyBottom(2)

        actionGroup {
          val actionManager = CommonActionsManager.getInstance()
          val treeExpander = DefaultTreeExpander(tree)
          add(actionManager.createExpandAllAction(treeExpander, tree))
          add(actionManager.createCollapseAllAction(treeExpander, tree))

          add(ActionManager.getInstance().createActionToolbar("GrazieRulesPanel", this, true).component, BorderLayout.WEST)
        }

        add(filter as Component, BorderLayout.CENTER)
      }

      panel(constraint = BorderLayout.CENTER) {
        add(ScrollPaneFactory.createScrollPane(tree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER))
      }
    }
  }

  val isModified: Boolean
    get() = tree.isModified

  fun state() = tree.state()

  fun addLang(lang: Lang) {
    tree.addLang(lang)
    update()
  }

  fun removeLang(lang: Lang) {
    tree.removeLang(lang)
    update()
  }

  fun update() {
    filter.filter()
    if (tree.isSelectionEmpty) tree.setSelectionRow(0)
  }

  fun reset() {
    tree.clearState()
    update()
  }

  fun filter(str: String) {
    filter.filter = str
    filter.filter()
  }

  override fun dispose() {
    filter.dispose()
  }
}
