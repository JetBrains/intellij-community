// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.*
import com.intellij.openapi.vcs.changes.actions.diff.SelectionAwareGoToChangePopupActionProvider
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.EditedCommitDetails
import com.intellij.vcs.commit.EditedCommitNode
import one.util.streamex.StreamEx
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

private fun wrap(project: Project, changesNodes: Stream<ChangesBrowserNode<*>>, unversioned: Stream<FilePath>): Stream<Wrapper> =
  Stream.concat(
    changesNodes.map { wrapNode(project, it) }.filter(Objects::nonNull),
    unversioned.map { UnversionedFileWrapper(it) }
  )

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

private class ChangesViewDiffPreviewProcessor(private val changesView: ChangesListView,
                                              isInEditor : Boolean) :
  ChangeViewDiffRequestProcessor(changesView.project, if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.CHANGES_VIEW) {

  init {
    if (!isInEditor) {
      myContentPanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
  }

  override fun getSelectedChanges(): Stream<Wrapper> =
    if (changesView.isSelectionEmpty) allChanges
    else wrap(project, StreamEx.of(changesView.selectedChangesNodes.iterator()), StreamEx.of(changesView.selectedUnversionedFiles.iterator()))

  override fun getAllChanges(): Stream<Wrapper> = wrap(project, StreamEx.of(changesView.changesNodes.iterator()), changesView.unversionedFiles)

  override fun selectChange(change: Wrapper) {
    changesView.findNodePathInTree(change.userObject, (change.tag as? ChangesBrowserNode.WrapperTag)?.value)
      ?.let {
        TreeUtil.selectPath(changesView, it, false)
        // Explicit refresh needed, since TreeUtil.selectPath will trigger refresh based on the current focused editor component.
        // This will fail in case if selection comes from "Go to Change" popup.
        refresh(false)
      }
  }

  override fun createGoToChangeAction(): AnAction {
    return MyGoToChangePopupProvider().createGoToChangeAction()
  }

  private inner class MyGoToChangePopupProvider : SelectionAwareGoToChangePopupActionProvider() {
    override fun getChanges(): List<PresentableChange> {
      return allChanges.toList()
    }

    override fun select(change: PresentableChange) {
      (change as? Wrapper)?.run(::selectChange)
    }

    override fun getSelectedChange(): PresentableChange? {
      return currentChange
    }
  }

  fun setAllowExcludeFromCommit(value: Boolean) {
    if (DiffUtil.isUserDataFlagSet(ALLOW_EXCLUDE_FROM_COMMIT, context) == value) return
    context.putUserData(ALLOW_EXCLUDE_FROM_COMMIT, value)
    fireDiffSettingsChanged()
  }

  fun fireDiffSettingsChanged() {
    dropCaches()
    updateRequest(true)
  }
}

private class AmendChangeWrapper(private val commitDetails: EditedCommitDetails) : ChangesBrowserNode.WrapperTag(commitDetails) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AmendChangeWrapper

    if (commitDetails.commit.id != other.commitDetails.commit.id) return false

    return true
  }

  override fun toString(): String = commitDetails.commit.subject

  override fun hashCode(): Int = commitDetails.commit.id.hashCode()
}

private class ChangeListWrapper(private val changeList: ChangeList) : ChangesBrowserNode.WrapperTag(changeList) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChangeListWrapper

    if (changeList.name != other.changeList.name) return false

    return true
  }

  override fun hashCode(): Int = changeList.name.hashCode()

  override fun toString(): String = changeList.name
}
