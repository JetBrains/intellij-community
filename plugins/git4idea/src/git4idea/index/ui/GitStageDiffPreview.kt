// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.runInEdtAsync
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.index.createTwoSidesDiffRequestProducer
import java.util.stream.Stream

class GitStageDiffPreview(project: Project, private val tree: GitStageTree, tracker: GitStageTracker, parent: Disposable) :
  ChangeViewDiffRequestProcessor(project, "Stage") {

  init {
    myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    tree.addSelectionListener(Runnable {
      val modelUpdateInProgress = tree.isModelUpdateInProgress
      runInEdtAsync(this) { updatePreview(component.isShowing, modelUpdateInProgress) }
    }, this)
    tracker.addListener(object : GitStageTrackerListener {
      override fun update() {
        updatePreview(component.isShowing, true)
      }
    }, this)
    Disposer.register(parent, this)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean = false

  fun getToolbarWrapper(): com.intellij.ui.components.panels.Wrapper = myToolbarWrapper

  override fun selectChange(change: Wrapper) {
    if (change !is GitFileStatusNodeWrapper && change !is ChangeWrapper) return
    val node = TreeUtil.findNodeWithObject(tree.root, change.userObject) ?: return
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
  }

  override fun getSelectedChanges(): Stream<Wrapper> =
    if (tree.isSelectionEmpty) allChanges else wrap(VcsTreeModelData.selected(tree))

  override fun getAllChanges(): Stream<Wrapper> = wrap(VcsTreeModelData.all(tree))

  private fun wrap(modelData: VcsTreeModelData): Stream<Wrapper> =
    Stream.concat(
      modelData.userObjectsStream(GitFileStatusNode::class.java).filter { it.kind != NodeKind.IGNORED }.map { GitFileStatusNodeWrapper(it) },
      modelData.userObjectsStream(Change::class.java).map { ChangeWrapper(it) }
    )

  private class GitFileStatusNodeWrapper(val node: GitFileStatusNode) : Wrapper() {
    override fun getPresentableName(): String = node.filePath.name

    override fun getUserObject(): Any = node

    override fun createProducer(project: Project?): DiffRequestProducer? {
      return createTwoSidesDiffRequestProducer(project!!, node)
    }
  }
}