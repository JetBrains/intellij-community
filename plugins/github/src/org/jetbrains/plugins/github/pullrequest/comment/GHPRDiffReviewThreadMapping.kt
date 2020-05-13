// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread

class GHPRDiffReviewThreadMapping(val diffSide: Side, val fileLineIndex: Int,
                                  val thread: GHPullRequestReviewThread)