// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModelBase
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAIReviewExtension
import org.jetbrains.plugins.github.ai.GHPRAIReviewViewModel
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel

@ApiStatus.Internal
interface GHPRViewModelContainer {
  val aiReviewVm: StateFlow<GHPRAIReviewViewModel?>

  val infoVm: GHPRInfoViewModel
  val branchWidgetVm: GHPRBranchWidgetViewModel
  val diffVm: GHPRDiffViewModel
  val editorVm: GHPRReviewInEditorViewModel
  val timelineVm: GHPRTimelineViewModel
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class GHPRViewModelContainerImpl(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  private val projectVm: GHPRToolWindowProjectViewModel,
  private val pullRequestId: GHPRIdentifier,
  cancelWith: Disposable
) : GHPRViewModelContainer {
  private val cs = parentCs.childScope(javaClass.name).cancelledWith(cancelWith)

  private val dataProvider: GHPRDataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequestId, cancelWith)

  private val diffSelectionRequests = MutableSharedFlow<ChangesSelection>(1)

  private val lazyInfoVm = lazy {
    GHPRInfoViewModel(project, cs, dataContext, dataProvider).apply {
      setup()
    }
  }
  override val infoVm: GHPRInfoViewModel by lazyInfoVm
  private val reviewVmHelper: GHPRReviewViewModelHelper by lazy { GHPRReviewViewModelHelper(cs, dataProvider) }

  override val aiReviewVm: StateFlow<GHPRAIReviewViewModel?> =
    GHPRAIReviewExtension.EP.extensionListFlow()
      .mapScoped { it.firstOrNull()?.provideReviewVm(project, this, infoVm) }
      .stateIn(cs, SharingStarted.Eagerly, null)

  private val branchStateVm by lazy {
    GHPRReviewBranchStateSharedViewModel(cs, dataContext, dataProvider)
  }
  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)
  override val branchWidgetVm: GHPRBranchWidgetViewModel by lazy {
    GHPRBranchWidgetViewModelImpl(cs, settings, dataProvider, projectVm, branchStateVm, reviewVmHelper, pullRequestId)
  }

  private val threadsVms = GHPRThreadsViewModels(project, cs, dataContext, dataProvider)
  override val diffVm: GHPRDiffViewModel by lazy {
    GHPRDiffViewModelImpl(project, cs, dataContext, dataProvider, reviewVmHelper, threadsVms).apply {
      setup()
    }
  }

  override val editorVm: GHPRReviewInEditorViewModel by lazy {
    GHPRReviewInEditorViewModelImpl(project, cs, settings, dataContext, dataProvider, branchStateVm, threadsVms) {
      diffSelectionRequests.tryEmit(it)
      projectVm.openPullRequestDiff(pullRequestId, true)
    }
  }

  override val timelineVm: GHPRTimelineViewModel by lazy {
    GHPRTimelineViewModelImpl(project, cs, dataContext, dataProvider).apply {
      setup()
    }
  }

  init {
    cs.launchNow {
      dataProvider.detailsData.stateChangeSignal.collectLatest {
        projectVm.refreshPrOnCurrentBranch()
      }
    }
  }

  private fun GHPRInfoViewModel.setup() {
    cs.launchNow {
      detailsVm.flatMapLatest { detailsVmResult ->
        detailsVmResult.getOrNull()?.changesVm?.changeListVm ?: flowOf(null)
      }.map {
        it?.getOrNull() as? CodeReviewChangeListViewModelBase
      }.collectScoped { vm ->
        vm?.handleSelection {
          if (it != null) {
            diffSelectionRequests.tryEmit(it)
          }
        }
      }
    }
  }

  private fun GHPRDiffViewModelImpl.setup() {
    cs.launchNow {
      diffSelectionRequests.collect {
        showDiffFor(it)
      }
    }

    cs.launchNow {
      diffVm.collectScoped {
        it.getOrNull()?.handleSelection { producer ->
          val change = producer?.asSafely<CodeReviewDiffRequestProducer>()?.change
          if (lazyInfoVm.isInitialized() && change != null) {
            lazyInfoVm.value.detailsVm.value.getOrNull()?.changesVm?.selectChange(change)
          }
        }
      }
    }
  }

  private fun GHPRTimelineViewModelImpl.setup() {
    cs.launchNow {
      showCommitRequests.collect {
        projectVm.viewPullRequest(pullRequestId, it)
      }
    }

    cs.launchNow {
      showDiffRequests.collect {
        diffVm.showDiffFor(it)
        projectVm.openPullRequestDiff(pullRequestId, true)
      }
    }
  }
}