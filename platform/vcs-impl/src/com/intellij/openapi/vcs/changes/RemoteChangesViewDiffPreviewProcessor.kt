// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.diff.DiffFilesCounterAction
import com.intellij.openapi.vcs.changes.actions.diff.DiffFilesCounterState
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.changes.UpdatableMultipleChangesDiffRequestProcessor
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.changes.ChangesViewChangeIdProvider
import com.intellij.vcs.changes.viewModel.RpcChangesViewProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny

/**
 * Diff preview of the Changes View in split mode, where the changes tree lives on the frontend.
 *
 * Unlike [ChangesViewDiffPreviewProcessor], which walks the tree, this processor is driven entirely by
 * [RpcChangesViewProxy.diffableSelection]: the frontend reports the selected file, the files reachable via
 * "Compare Previous/Next File", and the file counter values. Navigation is performed by asking the frontend to move
 * to the reported file via [RpcChangesViewProxy.selectPath], which keeps an explicit multiple selection intact.
 *
 * The "Go To Changed File" pop-up is not supported, only the file counter is shown, see [createGoToChangeAction].
 */
internal class RemoteChangesViewDiffPreviewProcessor(
  private val changesView: RpcChangesViewProxy,
  private val isInEditor: Boolean,
) : UpdatableMultipleChangesDiffRequestProcessor(changesView.project,
                                                 if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.CHANGES_VIEW) {
  private val changesCache by lazy { ChangesViewChangeIdProvider.getInstance(project) }

  /**
   * The file currently shown. Not read from [RpcChangesViewProxy.diffableSelection] directly: navigating with
   * "Compare Previous/Next File" moves it before the frontend reports the new selection back.
   */
  private var currentPath: ChangesTreePath? = null

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

  override fun getProject(): Project = changesView.project

  //
  // Update
  //

  @RequiresEdt
  override fun refresh(fromModelRefresh: Boolean) {
    if (isDisposed) return

    val selectedPath = changesView.diffableSelection.value?.selectedChange
    val current = currentPath
    if (fromModelRefresh && current != null && selectedPath != current &&
        context.isWindowFocused && context.isFocusedInWindow) {
      // Do not automatically switch the focused viewer: the user is likely to keep editing the same file.
      // Restore the frontend selection instead, so that it stays in sync with the shown file.
      if (selectedPath != null) changesView.selectPath(current)
      return
    }

    // Applied even when the path is unchanged: a [ChangesTreePath] outlives the [Change] it points at, so the request
    // has to be recreated from the refreshed model. Also lets the file counter pick up its new values.
    setCurrentPath(selectedPath)
  }

  @RequiresEdt
  override fun clear() {
    if (currentPath != null) {
      currentPath = null
      updateRequest()
    }
    dropCaches()
  }

  @RequiresEdt
  private fun setCurrentPath(path: ChangesTreePath?) {
    currentPath = path
    updateRequest()
  }

  override fun getCurrentRequestProvider(): DiffRequestProducer? = currentPath?.let(::createProducer)

  private fun createProducer(path: ChangesTreePath): DiffRequestProducer? {
    val changeId = path.changeId ?: return UnversionedDiffRequestProducer.create(project, path.filePath.filePath)
    val change = changesCache.getChangeListChange(changeId)
                 ?: changesCache.getEditedCommitDetailsChange(changeId)
                 ?: return null
    // Reuses the value class only for its producer, to keep the loading and error states in sync with monolith mode.
    return ChangeViewDiffRequestProcessor.ChangeWrapper(change).createProducer(project)
  }

  override fun loadRequestFast(provider: DiffRequestProducer): DiffRequest? {
    val request = super.loadRequestFast(provider)
    return if (ChangeViewDiffRequestProcessor.isRequestValid(request)) request else null
  }

  //
  // Presentation
  //

  @CalledInAny
  override fun getCurrentChangeName(): String? = currentPath?.filePath?.filePath?.name

  override fun getCurrentChangeIndex(): Int = changesView.diffableSelection.value?.selectedIndex ?: -1

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun isWindowFocused(): Boolean = DiffUtil.isFocusedComponent(project, component)

  private fun setAllowExcludeFromCommit(value: Boolean) {
    if (DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context) == value) return
    context.putUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, value)
    fireDiffSettingsChanged()
  }

  /**
   * The "Go To Changed File" pop-up is not supported, because it requires a lot of refactoring, so only the file
   * counter is shown.
   */
  override fun createGoToChangeAction(): AnAction = DiffFilesCounterAction(::getFilesCounterState)

  private fun getFilesCounterState(): DiffFilesCounterState? {
    // No diffable file is selected, e.g. the user selected a changelist with no files. Monolith mode shows "No files".
    val selection = changesView.diffableSelection.value ?: return DiffFilesCounterState.NO_FILES
    // The position is unknown, so the counter is hidden.
    val selectedIndex = selection.selectedIndex ?: return null
    if (selection.changesCount <= 0) return null
    return DiffFilesCounterState(selection.changesCount, selectedIndex)
  }

  private fun fireDiffSettingsChanged() {
    dropCaches()
    updateRequest(true)
  }

  //
  // Navigation
  //

  override fun isNavigationEnabled(): Boolean = true

  override fun hasNextChange(fromUpdate: Boolean): Boolean = selectionStrategy.canGoNext()

  override fun hasPrevChange(fromUpdate: Boolean): Boolean = selectionStrategy.canGoPrev()

  override fun goToNextChange(fromDifferences: Boolean) {
    goToNextChangeImpl(fromDifferences) { selectionStrategy.goNext() }
  }

  override fun goToPrevChange(fromDifferences: Boolean) {
    goToPrevChangeImpl(fromDifferences) { selectionStrategy.goPrev() }
  }

  private val selectionStrategy: PrevNextDifferenceIterable
    get() = DiffIterable(changesView.diffableSelection.value)

  private inner class DiffIterable(private val currentSelection: ChangesViewDiffableSelection?) : PrevNextDifferenceIterable {
    override fun canGoPrev(): Boolean = currentSelection?.previousChange != null

    override fun canGoNext(): Boolean = currentSelection?.nextChange != null

    override fun goPrev() {
      goTo(currentSelection?.previousChange)
    }

    override fun goNext() {
      goTo(currentSelection?.nextChange)
    }

    private fun goTo(path: ChangesTreePath?) {
      if (path == null) return
      setCurrentPath(path)
      changesView.selectPath(path)
    }
  }
}
