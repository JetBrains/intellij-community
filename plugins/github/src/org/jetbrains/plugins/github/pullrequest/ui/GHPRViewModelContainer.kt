// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.cancelledWith
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel

internal class GHPRViewModelContainer(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  pullRequestId: GHPRIdentifier,
  cancelWith: Disposable
) {
  private val cs = parentCs.childScope().cancelledWith(cancelWith)

  private val dataProvider: GHPRDataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequestId, cancelWith)

  val infoVm: GHPRInfoViewModel = GHPRInfoViewModel(project, cs, dataContext, dataProvider)
}