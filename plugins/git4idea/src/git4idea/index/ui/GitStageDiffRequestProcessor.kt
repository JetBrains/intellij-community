// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.ChangeWrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.tree.TreeUtil
import git4idea.index.*
import org.jetbrains.annotations.Nls
import java.util.*

class GitStageDiffRequestProcessor(val stageTree: GitStageTree,
                                   tracker: GitStageTracker,
                                   private val isInEditor: Boolean)
  : TreeHandlerDiffRequestProcessor("Stage", stageTree, GitStageDiffPreviewHandler) {

  init {
    GitStagingProcessorTracker(tracker, stageTree, this, handler).track()
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun forceKeepCurrentFileWhileFocused(): Boolean = true

  override fun createGoToChangeAction(): AnAction {
    return MyGoToChangePopupAction()
  }

  override fun loadRequestFast(provider: DiffRequestProducer): DiffRequest? {
    val request = super.loadRequestFast(provider) ?: return null
    if (!request.isValid()) return null
    return request
  }

  private fun DiffRequest.isValid(): Boolean {
    return getUserData(HEAD_INFO)?.isCurrent(project) ?: true
  }

  private inner class MyGoToChangePopupAction : PresentableGoToChangePopupAction.Default<Wrapper>() {
    override fun getChanges(): ListSelection<Wrapper> {
      return stageTree.listSelection(false).map {
        when (it) {
          is GitFileStatusNode -> GitFileStatusNodeWrapper(it)
          is Change -> ChangeWrapper(it)
          else -> null
        }
      }
    }

    override fun onSelected(change: Wrapper) {
      setCurrentChange(change)
      selectChange(change)
    }
  }
}

object GitStageDiffPreviewHandler : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): JBIterable<Wrapper> {
    return collectWrappers(VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(tree: ChangesTree): JBIterable<Wrapper> {
    return collectWrappers(VcsTreeModelData.all(tree))
  }

  private fun collectWrappers(modelData: VcsTreeModelData): JBIterable<Wrapper> {
    return JBIterable.empty<Wrapper>()
      .append(modelData.iterateUserObjects(GitFileStatusNode::class.java)
                .filter { it.kind != NodeKind.IGNORED }
                .map { GitFileStatusNodeWrapper(it) })
      .append(modelData.iterateUserObjects(Change::class.java)
                .map { ChangeWrapper(it) })
  }

  override fun selectChange(tree: ChangesTree, change: Wrapper) {
    if (change !is GitFileStatusNodeWrapper && change !is ChangeWrapper) return
    val node = TreeUtil.findNodeWithObject(tree.root, change.userObject) ?: return
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
  }
}

private class GitFileStatusNodeWrapper(val node: GitFileStatusNode) : Wrapper() {
  override fun getPresentableName(): @Nls String = node.filePath.name

  override fun getUserObject(): Any = node

  override fun getFilePath(): FilePath = node.filePath
  override fun getFileStatus(): FileStatus = node.fileStatus
  override fun getTag(): ChangesBrowserNode.Tag =
    KindTag.getTag(when (node.kind) {
                     NodeKind.UNTRACKED -> NodeKind.UNSTAGED
                     else -> node.kind
                   })

  override fun createProducer(project: Project?): DiffRequestProducer? {
    return createTwoSidesDiffRequestProducer(project!!, node)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitFileStatusNodeWrapper

    if (node.kind != other.node.kind) return false
    if (node.filePath != other.node.filePath) return false

    return true
  }

  override fun hashCode(): Int {
    return Objects.hash(node.kind, node.filePath)
  }

  override fun toString(): String {
    return "GitFileStatusNodeWrapper(node=$node)"
  }
}

class GitStagingProcessorTracker(val tracker: GitStageTracker,
                                 tree: ChangesTree,
                                 editorViewer: DiffEditorViewer,
                                 handler: ChangesTreeDiffPreviewHandler)
  : TreeHandlerChangesTreeTracker(tree, editorViewer, handler) {

  override fun track() {
    tracker.addListener(object : GitStageTrackerListener {
      override fun update() {
        updatePreviewLater(UpdateType.ON_MODEL_CHANGE)
      }
    }, editorViewer.disposable)

    super.track()
  }
}
