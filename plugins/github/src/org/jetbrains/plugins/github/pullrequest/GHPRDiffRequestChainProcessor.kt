// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

internal class GHPRDiffRequestChainProcessor(
  project: Project,
  private val diffRequestModel: GHPRDiffRequestModel
) : MutableDiffRequestChainProcessor(project, null) {
  private val diffChainUpdateQueue =
    MergingUpdateQueue("GHPRDiffRequestChainProcessor", 100, true, null, this).apply {
      setRestartTimerOnAdd(true)
    }

  init {
    diffRequestModel.addAndInvokeRequestChainListener(diffChainUpdateQueue) {
      val newChain = diffRequestModel.requestChain
      diffChainUpdateQueue.run(Update.create(diffRequestModel) {
        chain = newChain
      })
    }
  }

  override fun selectFilePath(filePath: FilePath) {
    diffRequestModel.selectedFilePath = filePath
  }
}
