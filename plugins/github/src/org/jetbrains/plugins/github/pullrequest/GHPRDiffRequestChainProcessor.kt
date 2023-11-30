// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.vcs.changes.ui.MutableDiffRequestChainProcessor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

fun GHPRDiffRequestModel.process(processor: MutableDiffRequestChainProcessor) {
  val updateQueue =
    MergingUpdateQueue("GHPRDiffRequestChainProcessor", 100, true, null, processor).apply {
      setRestartTimerOnAdd(true)
    }

  addAndInvokeRequestChainListener(updateQueue) {
    val newChain = requestChain
    updateQueue.run(Update.create(this) {
      processor.chain = newChain
    })
  }

  processor.selectionEventDispatcher.addListener {
    selectedFilePath = it.filePath
  }
}
