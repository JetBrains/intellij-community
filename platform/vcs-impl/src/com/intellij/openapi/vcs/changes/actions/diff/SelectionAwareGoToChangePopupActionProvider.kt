// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import javax.swing.tree.DefaultMutableTreeNode

abstract class SelectionAwareGoToChangePopupActionProvider {
  abstract fun getActualProducers(): List<@JvmWildcard DiffRequestProducer>

  abstract fun selectFilePath(filePath: FilePath)

  abstract fun getSelectedFilePath(): FilePath?

  fun createGoToChangeAction(): AnAction {
    return object : ChangeGoToChangePopupAction(getActualProducers()) {
      override fun onSelected(node: ChangesBrowserNode<*>?) {
        if (node is GenericChangesBrowserNode) {
          selectFilePath(node.filePath)
        }
      }

      override fun initialSelection(): Condition<in DefaultMutableTreeNode> {
        return Condition { node ->
          node is GenericChangesBrowserNode && node.filePath == getSelectedFilePath()
        }
      }
    }
  }
}
