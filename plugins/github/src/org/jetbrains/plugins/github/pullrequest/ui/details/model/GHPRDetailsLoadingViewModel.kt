// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel

internal interface GHPRDetailsLoadingViewModel {
  val isLoading: SharedFlow<Boolean>
  val detailsVm: SharedFlow<Result<GHPRDetailsViewModel>>

  fun requestReload()

  companion object {
    val DATA_KEY: DataKey<GHPRDetailsLoadingViewModel> = DataKey.create("GitHub.PullRequests.Details.Loading.ViewModel")
  }
}