// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.UnversionedViewDialog
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.impl.PlatformVcsPathPresenter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.FontUtil
import git4idea.i18n.GitBundle
import git4idea.index.GitFileStatus
import git4idea.index.GitStageTracker
import git4idea.index.actions.StagingAreaOperation
import git4idea.index.isRenamed
import git4idea.index.ui.NodeKind.Companion.sortOrder
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.PropertyKey
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.util.stream.Stream
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.streams.toList

val GIT_FILE_STATUS_NODES_STREAM = DataKey.create<Stream<GitFileStatusNode>>("GitFileStatusNodesStream")

abstract class GitStageTree(project: Project, parentDisposable: Disposable) : ChangesTree(project, false, true) {
  private var hoverNode: ChangesBrowserNode<*>? = null
    set(value) {
      if (field != value) {
        field = value
        repaint()
      }
    }
  protected abstract val state: GitStageTracker.State
  protected abstract val operations: List<StagingAreaOperation>

  init {
    setCellRenderer(GitStageTreeRenderer(ChangesBrowserNodeRenderer(myProject, { isShowFlatten }, true)))
    addMouseMotionListener(MyMouseMotionListener())
    MyClickListener().installOn(this)
    MyDnDSupport().install(parentDisposable)
  }

  abstract fun performStageOperation(nodes: List<GitFileStatusNode>, operation: StagingAreaOperation)

  abstract fun getDndOperation(targetKind: NodeKind): StagingAreaOperation?

  abstract fun showMergeDialog(conflictedFiles: List<VirtualFile>);

  override fun getComponentWidth(path: TreePath): Int {
    val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return 0
    return getFirstMatchingOperation(node)?.icon?.iconWidth ?: 0
  }

  internal fun getFirstMatchingOperation(node: ChangesBrowserNode<*>): StagingAreaOperation? {
    val statusNode = node.userObject as? GitFileStatusNode ?: return null
    return operations.find { it.matches(statusNode) }
  }

  fun update() {
    val state = TreeState.createOn(this, root)
    state.setScrollToSelection(false)
    rebuildTree()
    state.applyTo(this)
  }

  override fun rebuildTree() {
    val builder = MyTreeModelBuilder(myProject, groupingSupport.grouping)

    builder.createKindNode(NodeKind.STAGED)
    builder.createKindNode(NodeKind.UNSTAGED)

    state.rootStates.forEach { (root, rootState) ->
      rootState.statuses.forEach { (_, status) ->
        NodeKind.values().forEach { kind ->
          if (kind.`is`(status)) {
            builder.insertStatus(root, status, kind)
          }
        }
      }
    }

    updateTreeModel(builder.build())
  }

  override fun getData(dataId: String): Any? {
    return when {
      GIT_FILE_STATUS_NODES_STREAM.`is`(dataId) -> selectedStatusNodes()
      VcsDataKeys.FILE_PATH_STREAM.`is`(dataId) -> selectedStatusNodes().map { it.filePath }
      VcsDataKeys.VIRTUAL_FILE_STREAM.`is`(dataId) -> selectedStatusNodes().map { it.filePath.virtualFile }.filter { it != null }
      CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> selectedStatusNodes().map { it.filePath.virtualFile }.filter { it != null }
        .map { OpenFileDescriptor(project, it!!) }.toList().toTypedArray()
      else -> super.getData(dataId)
    }
  }

  fun selectedStatusNodes(): Stream<GitFileStatusNode> {
    return VcsTreeModelData.selected(this).userObjectsStream()
      .filter { it is GitFileStatusNode }
      .map { it as GitFileStatusNode }
  }

  private inner class MyTreeModelBuilder internal constructor(project: Project, grouping: ChangesGroupingPolicyFactory)
    : TreeModelBuilder(project, grouping) {
    private val parentNodes: MutableMap<NodeKind, ChangesBrowserKindNode> = mutableMapOf()
    private val untrackedFilesMap = mutableMapOf<VirtualFile, MutableCollection<GitFileStatus>>()

    fun insertStatus(root: VirtualFile, status: GitFileStatus, kind: NodeKind) {
      if (kind == NodeKind.UNTRACKED) {
        untrackedFilesMap.getOrPut(root) { mutableListOf() }.add(status)
      }
      else {
        insertFileStatusNode(GitFileStatusNode(root, status, kind), createKindNode(kind))
      }
    }

    private fun insertFileStatusNode(node: GitFileStatusNode, subtreeRoot: ChangesBrowserNode<*>) {
      insertChangeNode(node.filePath, subtreeRoot, ChangesBrowserGitFileStatusNode(node))
    }

    fun insertIntoRootNode(node: ChangesBrowserNode<*>) {
      myModel.insertNodeInto(node, myRoot, myRoot.childCount)
    }

    fun createKindNode(kind: NodeKind): ChangesBrowserKindNode {
      return parentNodes.getOrPut(kind) {
        ChangesBrowserKindNode(kind).also { insertIntoRootNode(it) }
      }
    }

    private fun createUntrackedNode() {
      val allUntrackedFiles = untrackedFilesMap.values.flatten()
      if (allUntrackedFiles.isEmpty()) return

      val untrackedRootNode = ChangesBrowserUntrackedNode(project, allUntrackedFiles.map { it.path }).also { insertIntoRootNode(it) }
      if (!untrackedRootNode.isManyFiles) {
        untrackedFilesMap.forEach { (root, untrackedInRoot) ->
          untrackedInRoot.forEach {
            insertFileStatusNode(GitFileStatusNode(root, it, NodeKind.UNTRACKED), untrackedRootNode)
          }
        }
      }
    }

    override fun build(): DefaultTreeModel {
      createUntrackedNode()

      return super.build()
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
      val origPath = userObject.origPath
      if (origPath == null || origPath.parentPath == userObject.filePath.parentPath) return null
      return PlatformVcsPathPresenter.getPresentableRelativePath(userObject.filePath, origPath)
    }
  }

  private open inner class ChangesBrowserKindNode(kind: NodeKind) : ChangesBrowserNode<NodeKind>(kind) {
    internal val kind: NodeKind
      get() = userObject as NodeKind

    init {
      markAsHelperNode()
    }

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      if (kind == NodeKind.CONFLICTED) {
        renderer.append(textPresentation, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        renderer.append(FontUtil.spaceAndThinSpace(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts.resolve.link.label"),
                        SimpleTextAttributes.LINK_BOLD_ATTRIBUTES,
                        Runnable {
                          val conflictedFiles = getObjectsUnderStream(GitFileStatusNode::class.java).map {
                            it.filePath.virtualFile
                          }.filter { it != null }.toList() as List<VirtualFile>
                          showMergeDialog(conflictedFiles)
                        })
        appendCount(renderer)
      }
      else {
        super.render(renderer, selected, expanded, hasFocus)
      }
    }

    @Nls
    override fun getTextPresentation(): String = GitBundle.message(kind.key)
    override fun compareUserObjects(o2: NodeKind?): Int {
      return Comparing.compare(sortOrder[kind], sortOrder[o2])
    }
  }

  private class ChangesBrowserUntrackedNode(project: Project, files: List<FilePath>) :
    ChangesBrowserSpecificFilePathsNode<NodeKind>(NodeKind.UNTRACKED, files, { UnversionedViewDialog(project, files).show() }) {
    init {
      markAsHelperNode()
    }

    @Nls
    override fun getTextPresentation(): String = GitBundle.message(NodeKind.UNTRACKED.key)
    override fun compareUserObjects(o2: NodeKind?): Int {
      return Comparing.compare(sortOrder[NodeKind.UNTRACKED], sortOrder[o2])
    }
  }

  private class GitStageTreeRenderer(textRenderer: ChangesBrowserNodeRenderer) :
    ChangesTreeCellRenderer<JBLabel>(textRenderer, JBLabel()) {

    override fun JBLabel.prepare(tree: ChangesTree, node: ChangesBrowserNode<*>) {
      val baseIcon = (tree as? GitStageTree)?.getFirstMatchingOperation(node)?.icon
      isVisible = baseIcon != null
      icon = baseIcon?.let { it -> activeIcon(it, tree).apply { setActive(tree.hoverNode == node) } }
    }

    private fun activeIcon(icon: Icon, component: JComponent) = ActiveIcon(icon, IconLoader.getDisabledIcon(icon, component))
  }

  private inner class MyMouseMotionListener : MouseMotionListener {

    override fun mouseMoved(e: MouseEvent?) {
      if (e == null) return
      val path = getPathIfInsideComponent(e.point)
      val node = path?.lastPathComponent as? ChangesBrowserNode<*>
      hoverNode = node

      if (node != null) {
        getFirstMatchingOperation(node)?.let {
          toolTipText = it.actionText.get()
        }
      }
    }

    override fun mouseDragged(e: MouseEvent?) = Unit
  }

  private inner class MyClickListener : ClickListener() {
    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
      val path: TreePath = getPathIfInsideComponent(event.point) ?: return false
      val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return false
      getFirstMatchingOperation(node)?.let {
        performStageOperation(listOf(node.userObject as GitFileStatusNode), it)
      }
      return false
    }
  }

  private inner class MyDnDSupport : ChangesTreeDnDSupport(this@GitStageTree) {
    override fun createDragStartBean(info: DnDActionInfo): DnDDragStartBean? {
      if (info.isMove) {
        val selection = selectedStatusNodes().toList()
        if (selection.isNotEmpty()) {
          return DnDDragStartBean(MyDragBean(this@GitStageTree, selection))
        }
      }
      return null
    }

    override fun canHandleDropEvent(aEvent: DnDEvent, dropNode: ChangesBrowserNode<*>): Boolean {
      val dragBean = aEvent.attachedObject
      if (dragBean is MyDragBean) {
        if (dragBean.sourceComponent === this@GitStageTree && canAcceptDrop(dropNode, dragBean)) {
          dragBean.targetNode = dropNode
          return true
        }
      }
      return false
    }

    override fun drop(aEvent: DnDEvent) {
      val dragBean = aEvent.attachedObject
      if (dragBean is MyDragBean) {
        val changesBrowserNode = dragBean.targetNode
        changesBrowserNode?.let { acceptDrop(it, dragBean) }
      }
    }

    private fun canAcceptDrop(node: ChangesBrowserNode<*>, bean: MyDragBean): Boolean {
      val targetKind: NodeKind = node.userObject as? NodeKind ?: return false
      val operation = getDndOperation(targetKind) ?: return false
      return bean.nodes.all(operation::matches)
    }

    private fun acceptDrop(node: ChangesBrowserNode<*>, bean: MyDragBean) {
      val targetKind: NodeKind = node.userObject as? NodeKind ?: return
      val operation = getDndOperation(targetKind) ?: return
      performStageOperation(bean.nodes, operation)
    }
  }

  private class MyDragBean(val tree: ChangesTree, val nodes: List<GitFileStatusNode>) {
    var targetNode: ChangesBrowserNode<*>? = null
    val sourceComponent: JComponent get() = tree
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

  companion object {
    internal val sortOrder = listOf(CONFLICTED, STAGED, UNSTAGED, UNTRACKED, IGNORED).zip(values().indices).toMap()
  }
}

data class GitFileStatusNode(val root: VirtualFile, val status: GitFileStatus, val kind: NodeKind) {
  val filePath: FilePath get() = status.path
  val origPath: FilePath? get() = kind.origPath(status)
  val fileStatus: FileStatus get() = kind.status(status)

  override fun toString(): String {
    return "GitFileStatusNode.Saved(root=$root, status=$fileStatus, kind=$kind)"
  }
}