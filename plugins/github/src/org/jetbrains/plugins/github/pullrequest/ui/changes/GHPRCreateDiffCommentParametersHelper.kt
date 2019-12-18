// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangedFileLinesMapper

class GHPRCreateDiffCommentParametersHelper(val commitSha: String, val filePath: String,
                                            private val linesMapper: GHPRChangedFileLinesMapper) {

  fun findPosition(diffSide: Side, sideFileLine: Int): Int? = linesMapper.findDiffLine(diffSide, sideFileLine)
}
