// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Side

interface GHPRChangedFileLinesMapper {
  fun findDiffLine(side: Side, fileLineIndex: Int): Int?
  fun findFileLocation(diffLineIndex: Int): Pair<Side, Int>?
}
