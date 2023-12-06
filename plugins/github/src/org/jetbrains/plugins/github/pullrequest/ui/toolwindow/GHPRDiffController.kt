// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import org.jetbrains.plugins.github.pullrequest.GHPRDiffRequestModel
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import javax.swing.event.TreeSelectionListener
import kotlin.properties.Delegates.observable


class GHPRDiffController(private val diffRequestModel: GHPRDiffRequestModel,
                         private val diffRequestProducer: DiffRequestChainProducer) {
  init {
    diffRequestModel.addFilePathSelectionListener {
      diffRequestModel.selectedFilePath?.let(::selectFilePath)
    }
  }

  var activeTree by observable<ActiveTree?>(null) { _, _, current ->
    val tree = when (current) {
      ActiveTree.FILES -> filesTree
      ActiveTree.COMMITS -> commitsTree
      else -> null
    }
    tree?.let { propagateSelection(it) }
  }

  var filesTree: ChangesTree? by observable(null) { _, oldValue, newValue ->
    oldValue?.removeTreeSelectionListener(filesTreeListener)
    newValue?.addTreeSelectionListener(filesTreeListener)
    filesTreeListener.valueChanged(null)
  }

  private val filesTreeListener = TreeSelectionListener { _ ->
    if (activeTree != ActiveTree.FILES) return@TreeSelectionListener
    filesTree?.let { propagateSelection(it) }
  }

  var commitsTree: ChangesTree? by observable(null) { _, oldValue, newValue ->
    oldValue?.removeTreeSelectionListener(commitsTreeListener)
    newValue?.addTreeSelectionListener(commitsTreeListener)
    commitsTreeListener.valueChanged(null)
  }

  private val commitsTreeListener = TreeSelectionListener { _ ->
    if (activeTree != ActiveTree.COMMITS) return@TreeSelectionListener
    commitsTree?.let { propagateSelection(it) }
  }

  private fun propagateSelection(tree: ChangesTree) {
    val selection = tree.let { VcsTreeModelData.getListSelectionOrAll(it).map { it as? Change } }
    // do not reset selection to zero
    if (!selection.isEmpty) diffRequestModel.requestChain = selection.let(diffRequestProducer::getRequestChain)
  }

  private fun selectFilePath(filePath: FilePath) {
    when (activeTree) {
      ActiveTree.COMMITS -> commitsTree
      ActiveTree.FILES -> filesTree
      else -> null
    }?.selectFile(filePath)
  }

  enum class ActiveTree {
    FILES, COMMITS
  }
}
