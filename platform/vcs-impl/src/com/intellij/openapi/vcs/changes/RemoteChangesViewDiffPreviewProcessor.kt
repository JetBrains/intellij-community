// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.util.cancelOnDispose
import com.intellij.vcs.changes.ChangesViewChangeIdProvider
import com.intellij.vcs.changes.viewModel.RpcChangesViewProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Reduced implementation of [ChangesViewDiffPreviewProcessor] interacting with [RpcChangesViewProxy] instead of the tree.
 * Its limitations are:
 * 1. "Go To Changed File" pop-up is not supported.
 * 2. Navigation between files is not limited by the current selection if multiple files are selected.
 *
 * This implementation relies on [RpcChangesViewProxy.diffableSelection] and invokes [RpcChangesViewProxy.selectPath] for
 * when next/previous file should be opened.
 */
internal class RemoteChangesViewDiffPreviewProcessor(
  private val changesView: RpcChangesViewProxy,
  private val isInEditor: Boolean,
) : ChangeViewDiffRequestProcessor(changesView.project, if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.CHANGES_VIEW) {
  private val changesCache by lazy { ChangesViewChangeIdProvider.getInstance(project) }

  init {
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)

    project.messageBus.connect(this).subscribe(LineStatusTrackerSettingListener.TOPIC,
                                               LineStatusTrackerSettingListener { fireDiffSettingsChanged() })

    launchInUiWithModelAccess {
      changesView.modelRefreshes.collectLatest {
        refresh(true)
      }
    }

    launchInUiWithModelAccess {
      changesView.diffableSelection.collectLatest {
        refresh(false)
      }
    }

    launchInUiWithModelAccess {
      project.serviceAsync<ChangesViewWorkflowManager>().allowExcludeFromCommit.collect {
        setAllowExcludeFromCommit(it)
      }
    }
  }

  private fun launchInUiWithModelAccess(block: suspend CoroutineScope.() -> Unit) {
    changesView.scope.launch(Dispatchers.UiWithModelAccess, block = block).cancelOnDispose(this)
  }

  override fun showAllChangesForEmptySelection(): Boolean = true

  override fun iterateSelectedChanges(): Iterable<Wrapper> {
    val selectedChange = changesView.diffableSelection.value?.selectedChange ?: return emptyList()
    return wrapChange(selectedChange)
  }

  private fun wrapChange(selectedChange: ChangesTreePath): List<Wrapper> {
    val changeId = selectedChange.changeId ?: return listOf(UnversionedFileWrapper(selectedChange.filePath.filePath))

    val changeListChange = changesCache.getChangeListChange(changeId)
    if (changeListChange != null) {
      return listOf(createChangeListWrapper(changeListChange))
    }

    val amendCommitChange = changesCache.getEditedCommitDetailsChange(changeId)
    if (amendCommitChange != null) {
      return listOf(createAmendCommitWrapper(amendCommitChange))
    }

    return emptyList()
  }

  private fun createChangeListWrapper(change: Change): Wrapper {
    val tag = (change as? ChangeListChange)
      ?.let { ChangeListManager.getInstance(project).getChangeList(it.changeListId) }
      ?.let { ChangeListWrapper(it) }
    return ChangeWrapper(change, tag)
  }

  private fun createAmendCommitWrapper(change: Change): Wrapper {
    val tag = (ChangesViewWorkflowManager.getInstance(project).editedCommit.value as? EditedCommitDetails)
      ?.let { AmendChangeWrapper(it) }
    return ChangeWrapper(change, tag)
  }

  override fun iterateAllChanges(): Iterable<Wrapper> = iterateSelectedChanges()

  // TODO amend node support
  override fun selectChange(change: Wrapper) {
    changesView.select(change.userObject)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun forceKeepCurrentFileWhileFocused(): Boolean = true

  private fun setAllowExcludeFromCommit(value: Boolean) {
    if (DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context) == value) return
    context.putUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, value)
    fireDiffSettingsChanged()
  }

  /**
   * Changes pop-up not supported
   */
  override fun createGoToChangeAction(): AnAction? = null

  override fun getSelectionStrategy(fromUpdate: Boolean): PrevNextDifferenceIterable =
    DiffIterable(changesView.diffableSelection.value)

  private fun fireDiffSettingsChanged() {
    dropCaches()
    updateRequest(true)
  }

  private inner class DiffIterable(private val currentSelection: ChangesViewDiffableSelection?): PrevNextDifferenceIterable {
    override fun canGoPrev(): Boolean = currentSelection != null && currentSelection.previousChange != null

    override fun canGoNext(): Boolean = currentSelection != null && currentSelection.nextChange != null

    override fun goPrev() {
      val previousChange = currentSelection?.previousChange
      if (previousChange != null) {
        changesView.selectPath(previousChange)
      }
    }

    override fun goNext() {
      val nextChange = currentSelection?.nextChange
      if (nextChange != null) {
        changesView.selectPath(nextChange)
      }
    }
  }
}