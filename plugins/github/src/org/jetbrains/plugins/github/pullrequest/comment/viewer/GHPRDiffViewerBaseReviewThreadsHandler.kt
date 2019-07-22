// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.base.DiffViewerBase
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping
import kotlin.properties.Delegates.observable

abstract class GHPRDiffViewerBaseReviewThreadsHandler<T : DiffViewerBase>(protected val viewer: T,
                                                                          protected val componentFactory: GHPREditorReviewThreadComponentFactory)
  : GHPRDiffViewerReviewThreadsHandler {

  protected abstract val viewerReady: Boolean

  override var mappings by observable<List<GHPRDiffReviewThreadMapping>>(emptyList()) { _, _, newValue ->
    if (viewerReady) updateThreads(newValue)
  }

  @CalledInAwt
  abstract fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>)

  override fun dispose() {}
}