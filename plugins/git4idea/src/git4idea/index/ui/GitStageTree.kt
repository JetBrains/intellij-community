// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.google.common.base.Objects
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.impl.PlatformVcsPathPresenter
import com.intellij.openapi.vfs.VirtualFile
import git4idea.i18n.GitBundle
import git4idea.index.GitFileStatus
import git4idea.index.GitStageTracker
import git4idea.index.isRenamed
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

abstract class GitStageTree(project: Project) : ChangesTree(project, false, true) {
  protected abstract val state: GitStageTracker.State

  fun update() {
    val state = TreeState.createOn(this, root)
    state.setScrollToSelection(false)
    rebuildTree()
    state.applyTo(this)
  }

  override fun rebuildTree() {
    val builder = MyTreeModelBuilder(myProject, groupingSupport.grouping)
    val parentNodes: MutableMap<NodeKind, ChangesBrowserKindNode> = mutableMapOf()

    state.gitState.forEach { (root, statuses) ->
      statuses.forEach { status ->
        NodeKind.values().forEach { kind ->
          if (kind.`is`(status)) {
            val parentNode = parentNodes.getOrPut(kind) { ChangesBrowserKindNode(kind) }
            val fileStatusInfo = GitFileStatusNode(root, status, kind)
            builder.insertPath(fileStatusInfo, parentNode)
          }
        }
      }
    }

    parentNodes.values.forEach { builder.insertIntoRootNode(it) }

    updateTreeModel(builder.build())
  }


  private inner class MyTreeModelBuilder internal constructor(project: Project, grouping: ChangesGroupingPolicyFactory) :
    TreeModelBuilder(project, grouping) {

    fun insertPath(node: GitFileStatusNode, parentNode: ChangesBrowserNode<*>) {
      insertChangeNode(node.filePath, parentNode, ChangesBrowserGitFileStatusNode(node))
    }

    fun insertIntoRootNode(node: ChangesBrowserNode<*>) {
      myModel.insertNodeInto(node, myRoot, myRoot.childCount)
    }
  }

  private class ChangesBrowserGitFileStatusNode(node: GitFileStatusNode) :
    AbstractChangesBrowserFilePathNode<GitFileStatusNode>(node, node.fileStatus) {
    private val movedRelativePath by lazy { getMovedRelativePath(getUserObject()) }
    override fun filePath(userObject: GitFileStatusNode): FilePath = userObject.filePath
    override fun originText(userObject: GitFileStatusNode): String? {
      val originalPath = userObject.origPath ?: return null
      if (movedRelativePath != null) {
        return VcsBundle.message("change.file.moved.from.text", movedRelativePath)
      }
      return VcsBundle.message("change.file.renamed.from.text", originalPath.name)
    }

    private fun getMovedRelativePath(userObject: GitFileStatusNode): String? {
      if (userObject.origPath == null || userObject.origPath!!.parentPath == userObject.filePath.parentPath) return null
      return PlatformVcsPathPresenter.getPresentableRelativePath(userObject.filePath, userObject.origPath!!)
    }
  }

  private class ChangesBrowserKindNode(kind: NodeKind) : ChangesBrowserNode<NodeKind>(kind) {
    private val sortOrder = listOf(NodeKind.CONFLICTED, NodeKind.STAGED,
                                   NodeKind.UNSTAGED, NodeKind.UNTRACKED,
                                   NodeKind.IGNORED).zip(NodeKind.values().indices).toMap()

    internal val kind: NodeKind
      get() = userObject as NodeKind

    init {
      markAsHelperNode()
    }

    @Nls
    override fun getTextPresentation(): String = GitBundle.message(kind.key)
    override fun compareUserObjects(o2: NodeKind?): Int {
      return Comparing.compare(sortOrder[kind], sortOrder[o2])
    }
  }
}

enum class NodeKind(@PropertyKey(resourceBundle = GitBundle.BUNDLE) @NonNls val key: String) {
  STAGED("stage.tree.node.staged") {
    override fun `is`(status: GitFileStatus) = status.getStagedStatus() != null
    override fun status(status: GitFileStatus) = status.getStagedStatus()!!
    override fun origPath(status: GitFileStatus): FilePath? = if (isRenamed(status.index)) status.origPath else null
  },
  UNSTAGED("stage.tree.node.unstaged") {
    override fun `is`(status: GitFileStatus): Boolean = status.getUnStagedStatus() != null
    override fun status(status: GitFileStatus) = status.getUnStagedStatus()!!
    override fun origPath(status: GitFileStatus): FilePath? = if (isRenamed(status.workTree)) status.origPath else null
  },
  CONFLICTED("stage.tree.node.unmerged") {
    override fun `is`(status: GitFileStatus): Boolean = status.isConflicted()
    override fun status(status: GitFileStatus) = FileStatus.MERGED_WITH_CONFLICTS
  },
  UNTRACKED("stage.tree.node.untracked") {
    override fun `is`(status: GitFileStatus): Boolean = status.isUntracked()
    override fun status(status: GitFileStatus) = FileStatus.UNKNOWN
  },
  IGNORED("stage.tree.node.ignored") {
    override fun `is`(status: GitFileStatus): Boolean = status.isIgnored()
    override fun status(status: GitFileStatus) = FileStatus.IGNORED
  };

  abstract fun `is`(status: GitFileStatus): Boolean
  abstract fun status(status: GitFileStatus): FileStatus
  open fun origPath(status: GitFileStatus): FilePath? = null
}

class GitFileStatusNode(val root: VirtualFile, val status: GitFileStatus, val kind: NodeKind) {
  val filePath: FilePath get() = status.path
  val origPath: FilePath? get() = kind.origPath(status)
  val fileStatus: FileStatus get() = kind.status(status)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitFileStatusNode

    if (root != other.root) return false
    if (status != other.status) return false
    if (kind != other.kind) return false

    return true
  }

  override fun hashCode(): Int {
    return Objects.hashCode(root, fileStatus, kind)
  }

  override fun toString(): String {
    return "GitFileStatusNode.Saved(root=$root, status=$fileStatus, kind=$kind)"
  }
}