// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.ChangeWrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.UnversionedFileWrapper
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.MODIFIED_WITHOUT_EDITING_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.TreeHandlerChangesTreeTracker
import com.intellij.openapi.vcs.changes.ui.TreeHandlerDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vcs.changes.ui.findAmendNode
import com.intellij.openapi.vcs.changes.ui.findChangeListNode
import com.intellij.openapi.vcs.changes.ui.findDiffGroupNode
import com.intellij.openapi.vcs.changes.ui.isUnderTag
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.util.cancelOnDispose
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.VcsDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.Objects

private fun wrap(
  project: Project,
  changesNodes: Iterable<ChangesBrowserNode<*>>,
  unversioned: Iterable<FilePath>,
): JBIterable<Wrapper> =
  JBIterable.empty<Wrapper>()
    .append(JBIterable.from(changesNodes).map { wrapNode(project, it) }.filter(Objects::nonNull))
    .append(JBIterable.from(unversioned).map { UnversionedFileWrapper(it) })

private fun wrapNode(project: Project, node: ChangesBrowserNode<*>): Wrapper? {
  return when (val nodeObject = node.userObject) {
    is Change -> ChangeWrapper(nodeObject, node.let(::wrapNodeToTag))
    is VirtualFile -> if (node.isUnderTag(MODIFIED_WITHOUT_EDITING_TAG)) wrapHijacked(project, nodeObject) else null
    else -> null
  }
}

private fun wrapHijacked(project: Project, file: VirtualFile): Wrapper? {
  return ChangesListView.toHijackedChange(project, file)
    ?.let { c -> ChangeWrapper(c, MODIFIED_WITHOUT_EDITING_TAG) }
}

private fun wrapNodeToTag(node: ChangesBrowserNode<*>): ChangesBrowserNode.Tag? {
  return node.findChangeListNode()?.let { ChangeListWrapper(it.userObject) }
         ?: node.findAmendNode()?.let { AmendChangeWrapper(it.userObject) }
}

@ApiStatus.Internal
class ChangesViewDiffPreviewProcessor(
  changesView: ChangesTree,
  private val isInEditor: Boolean,
) : TreeHandlerDiffRequestProcessor(if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.CHANGES_VIEW, changesView,
                                    ChangesViewDiffPreviewHandler) {

  private var allowExcludeFromCommit = false

  init {
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
    if (isInEditor) putContextUserData(STRUCTURAL_PREVIEW_CONTEXT, true)

    val busConnection = project.messageBus.connect(this)
    busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, MyLineStatusTrackerSettingsListener())
    busConnection.subscribe(ChangesViewModifier.TOPIC, ChangesViewModifier.ChangesViewModifierListener {
      UIUtil.invokeLaterIfNeeded { if (!isDisposed) updateAllowExcludeFromCommit() }
    })

    TreeHandlerChangesTreeTracker(tree, this, handler).track()
  }

  fun subscribeOnAllowExcludeFromCommit() {
    VcsDisposable.getInstance(project).coroutineScope.launch {
      project.serviceAsync<ChangesViewWorkflowManager>().allowExcludeFromCommit.collect {
        withContext(Dispatchers.EDT) {
          setAllowExcludeFromCommit(it)
        }
      }
    }.cancelOnDispose(this)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun forceKeepCurrentFileWhileFocused(): Boolean = true

  /**
   * With structural changes, the editor tab starts with the unified viewer. The tab keeps its own viewer order, so the viewer switch
   * still works and other diff tabs keep their choice. The superclass constructor calls this method, so it reads the place from the context.
   */
  override fun getSettings(): DiffSettingsHolder.DiffSettings {
    if (getContextUserData(DiffUserDataKeys.PLACE) != DiffPlaces.DEFAULT || !StructuralChangesViewSettings.isEnabled(project)) {
      return super.getSettings()
    }
    val settings = DiffSettingsHolder.DiffSettings.getSettings(STRUCTURAL_PREVIEW_PLACE)
    if (settings.diffToolsOrder.isEmpty()) settings.diffToolsOrder = listOf(UnifiedDiffTool::class.java.canonicalName)
    return settings
  }

  fun setAllowExcludeFromCommit(value: Boolean) {
    allowExcludeFromCommit = value
    updateAllowExcludeFromCommit()
  }

  /** With structural changes, the hunks in the tree have the checkboxes, so the editor tab shows none. */
  private fun updateAllowExcludeFromCommit() {
    val value = allowExcludeFromCommit && !(isInEditor && StructuralChangesViewSettings.isEnabled(project))
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
}

/** The viewer order of the Commit diff tab while structural changes are on. */
private const val STRUCTURAL_PREVIEW_PLACE = "ChangesView.StructuralPreview"

/** Marks the context of the Commit diff tab. [StructuralDiffPreviewAppearance] changes its viewers while structural changes are on. */
internal val STRUCTURAL_PREVIEW_CONTEXT: Key<Boolean> = Key.create("ChangesView.StructuralPreview")

internal object ChangesViewDiffPreviewHandler : ChangesTreeDiffPreviewHandler() {
  override val isShowAllChangesForEmptySelection: Boolean get() = false

  override fun iterateSelectedChanges(tree: ChangesTree): JBIterable<Wrapper> {
    val changesView = tree as? ChangesListView ?: return JBIterable.empty()
    return wrap(tree.project, changesView.selectedChangesNodes, changesView.selectedUnversionedFiles)
  }

  override fun iterateChangesFromSameGroup(tree: ChangesTree, change: Wrapper): JBIterable<Wrapper> {
    val node = TreeUtil.findNodeWithObject(tree.root, change.userObject) as? ChangesBrowserNode<*>
    val groupNode = node?.findDiffGroupNode() ?: return iterateSelectedChanges(tree)
    val model = VcsTreeModelData.allUnder(groupNode)
    return wrap(tree.project, model.iterateNodes(), model.iterateUserObjects(FilePath::class.java))
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

internal class AmendChangeWrapper(override val userObject: EditedCommitDetails) : ChangesViewUserObjectTag {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AmendChangeWrapper

    return userObject.commitHash == other.userObject.commitHash
  }

  override fun toString(): String = userObject.subject

  override fun hashCode(): Int = userObject.commitHash.hashCode()
}

internal class ChangeListWrapper(override val userObject: ChangeList) : ChangesViewUserObjectTag {
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
