// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.openapi.application.EDT
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.ai.GithubAIBundle
import javax.swing.JComponent
import javax.swing.JLabel

object GHPRAIReviewToolwindowComponentFactory {
  fun create(scope: CoroutineScope, vm: GHPRAIReviewToolwindowViewModel): JComponent {
    val panel = BorderLayoutPanel().apply {
      add(JLabel(GithubAIBundle.message("request.ai.review.in.review.toolwindow")))
    }
    scope.launch {
      vm.requestedReview.collect {
        withContext(Dispatchers.EDT) {
          panel.removeAll()
          if (it != null) {
            panel.addToCenter(GHPRAIReviewReviewComponentFactory.create(scope, it))
          }
          panel.revalidate()
          panel.repaint()
        }
      }
    }

    return panel
  }
}