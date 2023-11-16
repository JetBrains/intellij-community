// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt

interface GHPRDiffReviewSupport {
  @RequiresEdt
  fun install(viewer: DiffViewerBase)

  companion object {
    val KEY = Key.create<GHPRDiffReviewSupport>("Github.PullRequest.Diff.Review.Support")
  }
}