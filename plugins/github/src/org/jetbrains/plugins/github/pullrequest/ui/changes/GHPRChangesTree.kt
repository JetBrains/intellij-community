// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.SelectionSaver
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.BorderLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JPanel

object GHPRChangesTree {

  fun create(project: Project, model: SingleValueModel<List<Change>?>): ChangesTree {
    val tree = object : ChangesTree(project, false, false) {
      override fun rebuildTree() {
        updateTreeModel(TreeModelBuilder(project, grouping).setChanges(model.value.orEmpty(), null).build())
        if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
      }
      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }.also<ChangesTree> {
      SelectionSaver.installOn(it)
      it.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          if (it.isSelectionEmpty && !it.isEmpty) TreeUtil.selectFirstNode(it)
        }
      })
    }

    model.addValueChangedListener(tree::rebuildTree)
    tree.rebuildTree()
    return tree
  }

  fun createLazyTreePanel(model: SingleValueModel<List<Change>?>, treeSupplier: (JPanel) -> ChangesTree): JBPanelWithEmptyText {
    val wrapper = JBPanelWithEmptyText(BorderLayout()).apply {
      isOpaque = false
    }
    LazyPanelController(model, wrapper, treeSupplier)
    return wrapper
  }

  private class LazyPanelController(private val model: SingleValueModel<List<Change>?>,
                                    private val wrapper: JBPanelWithEmptyText,
                                    changesTreeFactory: (JPanel) -> ChangesTree) {

    private val tree by lazy(LazyThreadSafetyMode.NONE) {
      changesTreeFactory(wrapper)
    }

    init {
      model.addValueChangedListener(::update)
      update()
    }

    private fun update() {
      if (wrapper.componentCount > 1) return
      val changes = model.value
      if (changes == null) return

      wrapper.add(tree)
      wrapper.validate()
    }
  }
}