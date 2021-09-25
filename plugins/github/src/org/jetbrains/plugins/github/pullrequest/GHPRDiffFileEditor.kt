// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.plugins.github.i18n.GithubBundle

internal class GHPRDiffFileEditor(project: Project,
                                  private val diffRequestModel: GHPRDiffRequestModel,
                                  private val file: GHRepoVirtualFile)
  : FileEditorBase() {

  internal val diffProcessor = object : MutableDiffRequestChainProcessor(project, null) {
    override fun selectFilePath(filePath: FilePath) {
      diffRequestModel.selectedFilePath = filePath
    }
  }

  private val diffChainUpdateQueue =
    MergingUpdateQueue("updateDiffChainQueue", 100, true, null, this).apply {
      setRestartTimerOnAdd(true)
    }

  override fun isValid() = !Disposer.isDisposed(diffProcessor)

  init {
    diffRequestModel.addAndInvokeRequestChainListener(diffChainUpdateQueue) {
      val chain = diffRequestModel.requestChain
      diffChainUpdateQueue.run(Update.create(diffRequestModel) {
        diffProcessor.chain = chain
      })
    }
  }

  override fun getName(): String = GithubBundle.message("pull.request.editor.diff")

  override fun getComponent() = diffProcessor.component
  override fun getPreferredFocusedComponent() = diffProcessor.preferredFocusedComponent

  override fun selectNotify() = diffProcessor.updateRequest()

  override fun getFile() = file
}
