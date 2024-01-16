// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.diff.util.Side

sealed interface GHPRReviewCommentLocation {
  val side: Side
  val lineIdx: Int

  data class SingleLine(override val side: Side, override val lineIdx: Int) : GHPRReviewCommentLocation
  data class MultiLine(override val side: Side, val startLineIdx: Int, override val lineIdx: Int) : GHPRReviewCommentLocation
}