// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.model

import com.intellij.diff.util.Side

/**
 * Range of lines in file contents that could be mapped to GitHub diff file
 *
 * @param start - line index range start (inclusive)
 * @param end - line index range end (exclusive)
 * @param offset - can be added to content line index to get diff line index
 */
data class GHPRDiffRangeMapping(val commitSha: String, val filePath: String, val side: Side,
                                val start: Int, val end: Int, val offset: Int)