// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModelImpl

@ApiStatus.Experimental
class GHPRInfoViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider
) : GHPRDetailsLoadingViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  val pullRequest: GHPRIdentifier = dataProvider.id
  var pullRequestUrl: String? = null
    private set

  val repositoryRoot: String = dataContext.repositoryDataService.repositoryMapping.gitRepository.root.path

  //TODO: rework to detailsComputationFlow
  override val detailsVm: StateFlow<ComputedResult<GHPRDetailsViewModel>> = channelFlow<ComputedResult<GHPRDetailsViewModel>> {
    var vm: GHPRDetailsViewModelImpl? = null
    dataProvider.detailsData.detailsNeedReloadSignal.withInitial(Unit).collectLatest {
      if (vm == null) {
        send(ComputedResult.loading())
      }

      vm?.isUpdating?.value = true
      val toSend = try {
        val details = dataProvider.detailsData.loadDetails()
        pullRequestUrl = details.url
        val currentVm = vm
        if (currentVm == null) {
          val newVm = GHPRDetailsViewModelImpl(project, cs.childScope(), dataContext, dataProvider, details)
          vm = newVm
          ComputedResult.success(newVm)
        }
        else {
          currentVm.update(details)
          null
        }
      }
      catch (ce: CancellationException) {
        null
      }
      catch (e: Exception) {
        vm?.destroy()
        vm = null
        ComputedResult.failure(e)
      }
      finally {
        vm?.isUpdating?.value = false
      }
      if (toSend != null) {
        send(toSend)
      }
    }
  }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  val detailsLoadingErrorHandler: GHApiLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    cs.launch {
      dataProvider.detailsData.signalDetailsNeedReload()
      dataProvider.detailsData.signalMergeabilityNeedsReload()
    }
  }

  override fun requestReload() {
    cs.launch {
      dataProvider.detailsData.signalDetailsNeedReload()
      dataProvider.detailsData.signalMergeabilityNeedsReload()
      dataProvider.reviewData.signalPendingReviewNeedsReload()
      dataProvider.reviewData.signalThreadsNeedReload()
      dataProvider.changesData.signalChangesNeedReload()
      dataProvider.viewedStateData.signalViewedStateNeedsReload()
    }
  }
}