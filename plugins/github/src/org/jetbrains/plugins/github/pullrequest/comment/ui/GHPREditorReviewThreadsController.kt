// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.codereview.diff.EditorComponentInlaysManager

class GHPREditorReviewThreadsController(threadMap: GHPREditorReviewThreadsModel,
                                        private val componentFactory: GHPRDiffEditorReviewComponentsFactory,
                                        private val inlaysManager: EditorComponentInlaysManager) {

  private val inlayByThread = mutableMapOf<GHPRReviewThreadModel, Disposable>()

  init {
    for ((line, threads) in threadMap.modelsByLine) {
      for (thread in threads) {
        if (insertThread(line, thread)) break
      }
    }

    threadMap.addChangesListener(object : GHPREditorReviewThreadsModel.ChangesListener {
      override fun threadsAdded(line: Int, threads: List<GHPRReviewThreadModel>) {
        for (thread in threads) {
          insertThread(line, thread)
        }
      }

      override fun threadsRemoved(line: Int, threads: List<GHPRReviewThreadModel>) {
        for (thread in threads) {
          val inlay = inlayByThread.remove(thread) ?: continue
          Disposer.dispose(inlay)
        }
      }
    })
  }

  private fun insertThread(line: Int, thread: GHPRReviewThreadModel): Boolean {
    val component = componentFactory.createThreadComponent(thread)
    val inlay = inlaysManager.insertAfter(line, component) ?: return true
    inlayByThread[thread] = inlay
    return false
  }
}