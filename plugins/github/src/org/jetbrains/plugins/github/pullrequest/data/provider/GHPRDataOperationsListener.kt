// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.util.messages.Topic

interface GHPRDataOperationsListener {

  fun onStateChanged() {}
  fun onMetadataChanged() {}
  fun onCommentAdded() {}
  fun onCommentUpdated(commentId: String, newBody: String) {}
  fun onCommentDeleted(commentId: String) {}
  fun onReviewsChanged() {}
  fun onReviewUpdated(reviewId: String, newBody: String) {}

  companion object {
    val TOPIC = Topic.create("Pull Request data operations", GHPRDataOperationsListener::class.java)
  }
}