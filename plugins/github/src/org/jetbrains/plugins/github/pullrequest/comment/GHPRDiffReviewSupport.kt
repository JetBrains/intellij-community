// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.CalledInAwt

interface GHPRDiffReviewSupport {

  @get:CalledInAwt
  @set:CalledInAwt
  var showReviewThreads: Boolean

  @get:CalledInAwt
  val isLoadingReviewThreads: Boolean

  @CalledInAwt
  fun install(viewer: DiffViewerBase)

  @CalledInAwt
  fun reloadReviewThreads()

  companion object {
    val KEY = Key.create<GHPRDiffReviewSupport>("Github.PullRequest.Diff.Review.Support")
    val DATA_KEY = DataKey.create<GHPRDiffReviewSupport>("Github.PullRequest.Diff.Review.Support")
  }
}