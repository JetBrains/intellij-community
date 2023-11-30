// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.MutableCodeReviewChangeListViewModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.DiffPreview
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRDiffRequestChainProducer
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRProgressTreeModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupport
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupportImpl

@ApiStatus.Experimental
interface GHPRChangeListViewModel : CodeReviewChangeListViewModel, DiffPreview {
  val isUpdating: StateFlow<Boolean>

  val progressModel: GHPRProgressTreeModel

  fun canShowDiff(): Boolean
  fun showDiff()

  companion object {
    val DATA_KEY = DataKey.create<GHPRChangeListViewModel>("GitHub.PullRequest.Details.Changes.List.ViewModel")
  }
}

internal class GHPRChangeListViewModelImpl(
  parentCs: CoroutineScope,
  override val project: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : GHPRChangeListViewModel, MutableCodeReviewChangeListViewModel(parentCs) {
  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  private var commitSelected: Boolean = false

  private val _isUpdating = MutableStateFlow(false)
  override val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

  private val diffRequestProducer: GHPRDiffRequestChainProducer =
    object : GHPRDiffRequestChainProducer(project,
                                          dataProvider,
                                          dataContext.htmlImageLoader, dataContext.avatarIconsProvider,
                                          dataContext.repositoryDataService,
                                          dataContext.securityService.ghostUser,
                                          dataContext.securityService.currentUser) {

      private val viewedStateSupport = GHPRViewedStateDiffSupportImpl(repository, dataProvider.viewedStateData)

      override fun createCustomContext(change: Change): Map<Key<*>, Any> {
        if (commitSelected) return emptyMap()

        return mapOf(
          GHPRViewedStateDiffSupport.KEY to viewedStateSupport,
          GHPRViewedStateDiffSupport.PULL_REQUEST_FILE to ChangesUtil.getFilePath(change)
        )
      }
    }

  override val progressModel: GHPRProgressTreeModel =
    GHPRProgressTreeModel(repository, dataProvider.reviewData, dataProvider.viewedStateData) { !commitSelected }.also {
      Disposer.register(cs.nestedDisposable(), it)
    }

  fun setUpdating(updating: Boolean) {
    _isUpdating.value = updating
  }

  init {
    cs.launch {
      changesSelection.collect {
        val listSelection = it?.let { selection ->
          val changes = selection.changes
          val selectedIdx = selection.selectedIdx
          when (selection) {
            is ChangesSelection.Fuzzy -> ListSelection.createAt(changes, 0).asExplicitSelection()
            is ChangesSelection.Precise -> {
              val result = mutableListOf<Change>()
              for (i in changes.indices) {
                result.add(changes[i])
              }
              ListSelection.createAt(result, selectedIdx)
            }
          }
        }
        dataProvider.combinedDiffSelectionModel.updateSelectedChanges(it)
        dataProvider.diffRequestModel.requestChain = listSelection?.let(diffRequestProducer::getRequestChain)
      }
    }
  }

  override fun showDiffPreview() {
    dataContext.filesManager.createAndOpenDiffFile(dataProvider.id, true)
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    dataContext.filesManager.createAndOpenDiffFile(dataProvider.id, requestFocus)
    return true
  }

  override fun closePreview() = Unit

  override fun canShowDiff(): Boolean = dataProvider.diffRequestModel.requestChain != null

  override fun showDiff() {
    val requestChain = dataProvider.diffRequestModel.requestChain ?: return
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT)
  }
}