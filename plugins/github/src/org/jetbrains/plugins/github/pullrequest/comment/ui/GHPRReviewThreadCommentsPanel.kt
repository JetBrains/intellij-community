// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.util.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import javax.swing.JComponent
import javax.swing.ListModel

object GHPRReviewThreadCommentsPanel {
  fun create(commentsModel: ListModel<GHPRReviewCommentModel>,
             commentComponentFactory: (GHPRReviewCommentModel) -> JComponent): JComponent {

    if (commentsModel.size < 1) throw IllegalStateException("Thread cannot be empty")

    return TimelineThreadCommentsPanel(commentsModel, commentComponentFactory)
  }
}