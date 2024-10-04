// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.*
import com.intellij.openapi.vcs.changes.ChangesViewManager.ChangesViewToolWindowPanel
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.EditedCommitDetails
import com.intellij.vcs.commit.EditedCommitNode
import org.jetbrains.annotations.ApiStatus
import java.util.*

private fun wrap(project: Project,
                 changesNodes: Iterable<ChangesBrowserNode<*>>,
                 unversioned: Iterable<FilePath>): JBIterable<Wrapper> =
  JBIterable.empty<Wrapper>()
    .append(JBIterable.from(changesNodes).map { wrapNode(project, it) }.filter(Objects::nonNull))
    .append(JBIterable.from(unversioned).map { UnversionedFileWrapper(it) })

private fun wrapNode(project: Project, node: ChangesBrowserNode<*>): Wrapper? {
  return when (val nodeObject = node.userObject) {
    is Change -> ChangeWrapper(nodeObject, node.let(::wrapNodeToTag))
    is VirtualFile -> if (findTagNode(node)?.userObject == MODIFIED_WITHOUT_EDITING_TAG) wrapHijacked(project, nodeObject) else null
    else -> null
  }
}

private fun wrapHijacked(project: Project, file: VirtualFile): Wrapper? {
  return ChangesListView.toHijackedChange(project, file)
    ?.let { c -> ChangeWrapper(c, MODIFIED_WITHOUT_EDITING_TAG) }
}

private fun wrapNodeToTag(node: ChangesBrowserNode<*>): ChangesBrowserNode.Tag? {
  return findChangeListNode(node)?.let { ChangeListWrapper(it.userObject) }
         ?: findAmendNode(node)?.let { AmendChangeWrapper(it.userObject) }
}

private fun findTagNode(node: ChangesBrowserNode<*>): TagChangesBrowserNode? = findNodeOfType(node)
private fun findChangeListNode(node: ChangesBrowserNode<*>): ChangesBrowserChangeListNode? = findNodeOfType(node)
private fun findAmendNode(node: ChangesBrowserNode<*>): EditedCommitNode? = findNodeOfType(node)

private inline fun <reified T : ChangesBrowserNode<*>> findNodeOfType(node: ChangesBrowserNode<*>): T? {
  if (node is T) return node

  var parent = node.parent
  while (parent != null) {
    if (parent is T) return parent

    parent = parent.parent
  }

  return null
}

private class ChangesViewDiffPreviewProcessor(private val panel: ChangesViewManager.ChangesViewToolWindowPanel,
                                              changesView: ChangesListView,
                                              private val isInEditor: Boolean)
  : TreeHandlerDiffRequestProcessor(if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.CHANGES_VIEW, changesView,
                                    ChangesViewDiffPreviewHandler) {

  init {
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)

    val busConnection = project.messageBus.connect(this)
    busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, MyLineStatusTrackerSettingsListener())

    panel.addListener(MyCommitModeListener(), this)
    setAllowExcludeFromCommit(panel.isAllowExcludeFromCommit)

    TreeHandlerChangesTreeTracker(tree, this, handler).track()
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun showAllChangesForEmptySelection(): Boolean = false

  override fun forceKeepCurrentFileWhileFocused(): Boolean = true

  private fun setAllowExcludeFromCommit(value: Boolean) {
    if (DiffUtil.isUserDataFlagSet(ALLOW_EXCLUDE_FROM_COMMIT, context) == value) return
    context.putUserData(ALLOW_EXCLUDE_FROM_COMMIT, value)
    fireDiffSettingsChanged()
  }

  private fun fireDiffSettingsChanged() {
    dropCaches()
    updateRequest(true)
  }

  private inner class MyLineStatusTrackerSettingsListener : LineStatusTrackerSettingListener {
    override fun settingsUpdated() {
      fireDiffSettingsChanged()
    }
  }

  private inner class MyCommitModeListener : ChangesViewToolWindowPanel.Listener {
    override fun allowExcludeFromCommitChanged() {
      setAllowExcludeFromCommit(panel.isAllowExcludeFromCommit)
    }
  }
}

internal object ChangesViewDiffPreviewHandler : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): JBIterable<Wrapper> {
    val changesView = tree as? ChangesListView ?: return JBIterable.empty()
    return wrap(tree.project, changesView.selectedChangesNodes, changesView.selectedUnversionedFiles)
  }

  override fun iterateAllChanges(tree: ChangesTree): JBIterable<Wrapper> {
    val changesView = tree as? ChangesListView ?: return JBIterable.empty()
    return wrap(tree.project, changesView.changesNodes, changesView.unversionedFiles)
  }

  override fun selectChange(tree: ChangesTree, change: Wrapper) {
    val tag = (change.tag as? ChangesViewUserObjectTag)?.userObject
    val changesView = tree as? ChangesListView ?: return
    val treePath = changesView.findNodePathInTree(change.userObject, tag) ?: return
    TreeUtil.selectPath(changesView, treePath, false)
  }
}

private class AmendChangeWrapper(override val userObject: EditedCommitDetails) : ChangesViewUserObjectTag {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AmendChangeWrapper

    return userObject.commit.id == other.userObject.commit.id
  }

  override fun toString(): String = userObject.commit.subject

  override fun hashCode(): Int = userObject.commit.id.hashCode()
}

private class ChangeListWrapper(override val userObject: ChangeList) : ChangesViewUserObjectTag {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChangeListWrapper

    return userObject.name == other.userObject.name
  }

  override fun hashCode(): Int = userObject.name.hashCode()

  override fun toString(): String = userObject.name
}

@ApiStatus.Internal
interface ChangesViewUserObjectTag : ChangesBrowserNode.Tag {
  val userObject: Any
}
