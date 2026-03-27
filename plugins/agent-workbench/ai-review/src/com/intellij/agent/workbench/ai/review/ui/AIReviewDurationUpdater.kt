// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.analysis.problemsView.toolWindow.Node
import com.intellij.analysis.problemsView.toolWindow.ProblemsTreeModel
import com.intellij.openapi.Disposable
import javax.swing.Timer

internal class AIReviewDurationUpdater(
  private val treeModel: ProblemsTreeModel,
  private val rootNode: Node,
) : Disposable {
  private val repaintTimer = Timer(1000) {
    rootNode.update()
    treeModel.nodeChanged(rootNode.getPath())
  }.apply { isRepeats = true }

  @Volatile
  var startTimestamp: Long? = null
    private set

  fun ensureStarted() {
    if (startTimestamp == null) {
      startTimestamp = System.currentTimeMillis()
    }
    if (!repaintTimer.isRunning) {
      repaintTimer.start()
    }
  }

  fun stop() {
    startTimestamp = null
    repaintTimer.stop()
  }

  override fun dispose() {
    stop()
  }
}
