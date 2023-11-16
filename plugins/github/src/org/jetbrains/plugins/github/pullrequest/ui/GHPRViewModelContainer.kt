// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.model.getSelected
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel

@OptIn(ExperimentalCoroutinesApi::class)
internal class GHPRViewModelContainer(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  pullRequestId: GHPRIdentifier,
  cancelWith: Disposable
) {
  private val cs = parentCs.childScope().cancelledWith(cancelWith)

  private val dataProvider: GHPRDataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequestId, cancelWith)

  private val diffSelectionRequests = MutableSharedFlow<ChangesSelection>(1)

  private val lazyInfoVm = lazy {
    GHPRInfoViewModel(project, cs, dataContext, dataProvider)
  }
  val infoVm: GHPRInfoViewModel by lazyInfoVm

  private val reviewVmHelper = GHPRReviewViewModelHelper(cs, dataProvider)
  val diffVm: GHPRDiffViewModel by lazy {
    GHPRDiffViewModelImpl(project, cs, dataContext, dataProvider, reviewVmHelper).apply {
      setup()
    }
  }

  init {
    cs.launchNow {
      infoVm.detailsVm.flatMapLatest { detailsVmResult ->
        detailsVmResult.getOrNull()?.changesVm?.changeListVm?.flatMapLatest {
          it.getOrNull()?.changesSelection ?: flowOf(null)
        } ?: flowOf(null)
      }.filterNotNull().collect(diffSelectionRequests)
    }
  }

  private fun GHPRDiffViewModelImpl.setup() {
    cs.launchNow {
      diffSelectionRequests.collect {
        showDiffFor(it)
      }
    }

    cs.launchNow {
      diffVm.flatMapLatest {
        it.result?.getOrNull()?.producers?.map { state -> (state.getSelected() as? CodeReviewDiffRequestProducer)?.change } ?: flowOf(null)
      }.collectLatest {
        if (lazyInfoVm.isInitialized() && it != null) {
          lazyInfoVm.value.detailsVm.value.getOrNull()?.changesVm?.selectChange(it)
        }
      }
    }
  }
}