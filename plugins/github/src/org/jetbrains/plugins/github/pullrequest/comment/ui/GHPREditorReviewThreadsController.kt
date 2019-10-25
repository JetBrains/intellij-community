// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer

class GHPREditorReviewThreadsController(threadMap: GHPREditorReviewThreadsModel,
                                        private val componentFactory: GHPRDiffEditorReviewComponentsFactory,
                                        private val inlaysManager: EditorComponentInlaysManager) {

  private val inlayByThread = mutableMapOf<GHPRReviewThreadModel, Inlay<*>>()

  init {
    for ((line, threads) in threadMap.modelsByLine) {
      for (thread in threads) {
        val inlay = inlaysManager.insertAfter(line, componentFactory.createThreadComponent(thread)) ?: break
        inlayByThread[thread] = inlay
      }
    }

    threadMap.addChangesListener(object : GHPREditorReviewThreadsModel.ChangesListener {
      override fun threadsAdded(line: Int, threads: List<GHPRReviewThreadModel>) {
        for (thread in threads) {
          val inlay = inlaysManager.insertAfter(line, componentFactory.createThreadComponent(thread)) ?: break
          inlayByThread[thread] = inlay
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
}