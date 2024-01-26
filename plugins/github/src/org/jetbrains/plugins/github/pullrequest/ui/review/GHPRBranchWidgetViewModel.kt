// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.computationState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.util.ComputedResult
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transform
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.changesRequestFlow
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel

interface GHPRBranchWidgetViewModel {
  val id: GHPRIdentifier

  val updateRequired: StateFlow<Boolean>
  val dataLoadingState: StateFlow<ComputedResult<Any>>

  fun showPullRequest()
}

internal class GHPRBranchWidgetViewModelImpl(
  parentCs: CoroutineScope,
  dataProvider: GHPRDataProvider,
  private val projectVm: GHPRToolWindowProjectViewModel,
  override val id: GHPRIdentifier
) : GHPRBranchWidgetViewModel {
  private val cs = parentCs.childScope(classAsCoroutineName())

  override val updateRequired: StateFlow<Boolean> = dataProvider.changesData.newChangesInReviewRequest.transform {
    val result = runCatching {
      it.await()
    }.fold({ it }, { false })
    emit(result)
  }.stateInNow(cs, false)

  override val dataLoadingState: StateFlow<ComputedResult<Any>> =
    dataProvider.changesData.changesRequestFlow().computationState().stateInNow(cs, ComputedResult.loading())

  override fun showPullRequest() {
    projectVm.viewPullRequest(id)
  }
}
