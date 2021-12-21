// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.IgnoredViewDialog
import com.intellij.openapi.vcs.changes.UnversionedViewDialog
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import git4idea.conflicts.GitConflictsUtil.getConflictType
import git4idea.i18n.GitBundle
import git4idea.index.GitFileStatus
import git4idea.index.GitStageTracker
import git4idea.index.actions.StagingAreaOperation
import git4idea.index.ignoredStatus
import git4idea.index.isRenamed
import git4idea.index.ui.NodeKind.Companion.sortOrder
import git4idea.repo.GitConflict
import git4idea.status.GitStagingAreaHolder
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import javax.swing.JComponent
import javax.swing.tree.DefaultTreeModel

abstract class GitStageTree(project: Project,
                            private val settings: GitStageUiSettings,
                            parentDisposable: Disposable) :
  HoverChangesTree(project, false, true) {

  protected abstract val state: GitStageTracker.State
  protected abstract val ignoredFilePaths: Map<VirtualFile, List<FilePath>>
  protected abstract val operations: List<StagingAreaOperation>

  init {
    isKeepTreeState = true
    isScrollToSelection = false

    MyDnDSupport().install(parentDisposable)
    settings.addListener(object : GitStageUiSettingsListener {
      override fun settingsChanged() {
        rebuildTree()
      }
    }, parentDisposable)
  }

  override fun getToggleClickCount(): Int = 2

  protected abstract fun performStageOperation(nodes: List<GitFileStatusNode>, operation: StagingAreaOperation)

  protected abstract fun getDndOperation(targetKind: NodeKind): StagingAreaOperation?

  protected abstract fun showMergeDialog(conflictedFiles: List<VirtualFile>)

  protected abstract fun createHoverIcon(node: ChangesBrowserGitFileStatusNode): HoverIcon?

  override fun getHoverIcon(node: ChangesBrowserNode<*>): HoverIcon? {
    if (node == root) return null
    if (node is ChangesBrowserGitFileStatusNode) {
      val hoverIcon = createHoverIcon(node)
      if (hoverIcon != null) return hoverIcon
    }
    val statusNode = VcsTreeModelData.children(node).userObjectsStream(GitFileStatusNode::class.java).findFirst().orElse(null)
                     ?: return null
    val operation = operations.find { it.matches(statusNode) } ?: return null
    if (operation.icon == null) return null
    return GitStageHoverIcon(operation)
  }

  override fun rebuildTree() {
    val builder = MyTreeModelBuilder(myProject, groupingSupport.grouping)

    builder.createKindNode(NodeKind.STAGED)
    builder.createKindNode(NodeKind.UNSTAGED)

    state.forEachStatus(*NodeKind.values()) { root, status, kind ->
      builder.insertStatus(root, status, kind)
    }

    if (settings.ignoredFilesShown()) {
      builder.insertIgnoredPaths(ignoredFilePaths)
    }

    customizeTreeModel(builder)
    updateTreeModel(builder.build())
  }

  protected open fun customizeTreeModel(builder: TreeModelBuilder) = Unit

  override fun getData(dataId: String): Any? {
    return when {
      GitStageDataKeys.GIT_STAGE_TREE.`is`(dataId) -> this
      GitStageDataKeys.GIT_STAGE_UI_SETTINGS.`is`(dataId) -> settings
      GitStageDataKeys.GIT_FILE_STATUS_NODES.`is`(dataId) -> selectedStatusNodes()
      VcsDataKeys.FILE_PATHS.`is`(dataId) -> selectedStatusNodes().map { it.filePath }
      VcsDataKeys.VIRTUAL_FILES.`is`(dataId) -> selectedStatusNodes().map { it.filePath.virtualFile }.filter { it != null }
      CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> selectedStatusNodes().map { it.filePath.virtualFile }.filter { it != null }
        .toList().toTypedArray()
      CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> selectedStatusNodes().map { it.filePath.virtualFile }.filter { it != null }
        .map { OpenFileDescriptor(project, it!!) }.toList().toTypedArray()
      PlatformDataKeys.DELETE_ELEMENT_PROVIDER.`is`(dataId) -> if (!selectedStatusNodes().isEmpty) VirtualFileDeleteProvider() else null
      else -> super.getData(dataId)
    }
  }

  fun selectedStatusNodes(): JBIterable<GitFileStatusNode> {
    val data = VcsTreeModelData.selected(this)
    return JBIterable.create { data.userObjectsStream(GitFileStatusNode::class.java).iterator() }
  }

  fun statusNodesListSelection(preferLimitedContext: Boolean): ListSelection<GitFileStatusNode> {
    val entries = VcsTreeModelData.selected(this).userObjects(GitFileStatusNode::class.java)
    if (entries.size > 1) {
      return ListSelection.createAt(entries, 0)
    }

    val selected = entries.singleOrNull()
    val selectedKind = selected?.kind

    val allEntriesData: VcsTreeModelData = when {
      preferLimitedContext && (selectedKind == NodeKind.UNSTAGED || selectedKind == NodeKind.UNTRACKED) -> {
        VcsTreeModelData.allUnderTag(this, NodeKind.UNSTAGED)
      }
      preferLimitedContext && (selectedKind == NodeKind.STAGED || selectedKind == NodeKind.IGNORED || selectedKind == NodeKind.CONFLICTED) -> {
        VcsTreeModelData.allUnderTag(this, selectedKind)
      }
      else -> {
        VcsTreeModelData.all(this)
      }
    }

    val allEntries = allEntriesData.userObjects(GitFileStatusNode::class.java)
    return if (allEntries.size <= entries.size) {
      ListSelection.createAt(entries, 0)
    }
    else {
      ListSelection.create(allEntries, selected)
    }
  }

  private inner class GitStageHoverIcon(val operation: StagingAreaOperation)
    : HoverIcon(operation.icon!!, operation.actionText.get()) {
    override fun invokeAction(node: ChangesBrowserNode<*>) {
      val nodes = VcsTreeModelData.children(node).userObjects(GitFileStatusNode::class.java)
      performStageOperation(nodes, operation)
    }

    override fun equals(other: Any?): Boolean = other is GitStageHoverIcon &&
                                                operation == other.operation

    override fun hashCode(): Int = operation.hashCode()
  }

  private inner class MyTreeModelBuilder(project: Project, grouping: ChangesGroupingPolicyFactory)
    : TreeModelBuilder(project, grouping) {
    private val parentNodes: MutableMap<NodeKind, MyKindNode> = mutableMapOf()
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

    fun createKindNode(kind: NodeKind): MyKindNode {
      return parentNodes.getOrPut(kind) {
        MyKindNode(kind).also { insertIntoRootNode(it) }
      }
    }

    fun insertIgnoredPaths(ignoredFiles: Map<VirtualFile, List<FilePath>>) {
      val allIgnored = ignoredFiles.values.flatten()
      if (ContainerUtil.isEmpty(allIgnored)) return

      val ignoredNode = MyIgnoredNode(project, allIgnored).also { insertIntoRootNode(it) }
      if (!ignoredNode.isManyFiles) {
        ignoredFiles.forEach { (root, ignoredInRoot) ->
          ignoredInRoot.forEach {
            insertFileStatusNode(GitFileStatusNode(root, ignoredStatus(it), NodeKind.IGNORED), ignoredNode)
          }
        }
      }
    }

    private fun createUntrackedNode() {
      val allUntrackedStatuses = untrackedFilesMap.values.flatten()
      if (allUntrackedStatuses.isEmpty()) return

      if (ChangesBrowserSpecificFilePathsNode.isManyFiles(allUntrackedStatuses)) {
        MyUntrackedNode(project, allUntrackedStatuses.map { it.path }).also { insertIntoRootNode(it) }
      }
      else {
        val unstagedNode = createKindNode(NodeKind.UNSTAGED)
        untrackedFilesMap.forEach { (root, untrackedInRoot) ->
          untrackedInRoot.forEach {
            insertFileStatusNode(GitFileStatusNode(root, it, NodeKind.UNTRACKED), unstagedNode)
          }
        }
      }
    }

    override fun build(): DefaultTreeModel {
      createUntrackedNode()

      return super.build()
    }
  }

  protected class ChangesBrowserGitFileStatusNode(node: GitFileStatusNode) :
    AbstractChangesBrowserFilePathNode<GitFileStatusNode>(node, node.fileStatus) {

    internal val conflict by lazy { getUserObject().createConflict() }

    override fun filePath(userObject: GitFileStatusNode): FilePath = userObject.filePath

    override fun originPath(userObject: GitFileStatusNode): FilePath? = userObject.origPath

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      super.render(renderer, selected, expanded, hasFocus)

      conflict?.let { conflict ->
        renderer.append(FontUtil.spaceAndThinSpace() + getConflictType(conflict), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

    override fun appendParentPath(renderer: ChangesBrowserNodeRenderer, parentPath: FilePath?) {
      if (conflict == null) {
        super.appendParentPath(renderer, parentPath)
      }
    }
  }

  protected open inner class MyKindNode(kind: NodeKind) : ChangesBrowserNode<NodeKind>(kind) {
    val kind: NodeKind
      get() = userObject as NodeKind

    init {
      markAsHelperNode()
    }

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.append(textPresentation, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      if (kind == NodeKind.CONFLICTED) {
        renderer.append(FontUtil.spaceAndThinSpace(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts.resolve.link.label"),
                        SimpleTextAttributes.LINK_BOLD_ATTRIBUTES,
                        Runnable {
                          val conflictedFiles = traverseObjectsUnder().filter(GitFileStatusNode::class.java).map {
                            it.filePath.virtualFile
                          }.filter { it != null }.toList() as List<VirtualFile>
                          showMergeDialog(conflictedFiles)
                        })
      }
      appendCount(renderer)
    }

    @Nls
    override fun getTextPresentation(): String = GitBundle.message(kind.key)
    override fun getSortWeight(): Int = sortOrder.getValue(kind)
  }

  private class MyIgnoredNode(project: Project, files: List<FilePath>) :
    ChangesBrowserSpecificFilePathsNode<NodeKind>(NodeKind.IGNORED, files, { IgnoredViewDialog(project).show() }) {
    init {
      markAsHelperNode()
      setAttributes(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

    @Nls
    override fun getTextPresentation(): String = GitBundle.message(NodeKind.IGNORED.key)
    override fun getSortWeight(): Int = sortOrder.getValue(NodeKind.IGNORED)
  }

  private class MyUntrackedNode(project: Project, files: List<FilePath>) :
    ChangesBrowserSpecificFilePathsNode<NodeKind>(NodeKind.UNTRACKED, files, { UnversionedViewDialog(project, files).show() }) {
    init {
      markAsHelperNode()
      setAttributes(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

    @Nls
    override fun getTextPresentation(): String = GitBundle.message(NodeKind.UNTRACKED.key)
    override fun getSortWeight(): Int = sortOrder.getValue(NodeKind.UNTRACKED)
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

    override fun canHandleDropEvent(aEvent: DnDEvent, dropNode: ChangesBrowserNode<*>?): Boolean {
      val dragBean = aEvent.attachedObject
      if (dragBean is MyDragBean) {
        if (dropNode != null && dragBean.sourceComponent === this@GitStageTree && canAcceptDrop(dropNode, dragBean)) {
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

  override fun toString(): @NonNls String {
    return "GitFileStatusNode(root=${root.name}, status=$fileStatus, kind=$kind, path=$filePath)"
  }
}

internal fun GitStageTracker.State.fileStatusNodes(vararg kinds: NodeKind): List<GitFileStatusNode> {
  val result = mutableListOf<GitFileStatusNode>()
  forEachStatus(*kinds) { root, status, kind ->
    result.add(GitFileStatusNode(root, status, kind))
  }
  return result
}

internal fun GitStageTracker.State.forEachStatus(vararg kinds: NodeKind, function: (VirtualFile, GitFileStatus, NodeKind) -> Unit) {
  rootStates.forEach { (root, rootState) ->
    rootState.statuses.forEach { (_, status) ->
      kinds.forEach { kind ->
        if (kind.`is`(status)) {
          function(root, status, kind)
        }
      }
    }
  }
}

internal fun GitStageTracker.State.hasMatchingRoots(vararg kinds: NodeKind): Boolean {
  return rootStates.values.any { rootState -> rootState.statuses.values.any { status -> kinds.any { it.`is`(status) } } }
}

internal fun GitFileStatusNode.createConflict(): GitConflict? {
  return GitStagingAreaHolder.createConflict(root, status)
}