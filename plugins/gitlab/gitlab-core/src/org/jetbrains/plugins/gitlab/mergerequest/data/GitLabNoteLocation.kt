// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.diff.util.Side

data class GitLabNoteLocation(
  val startSide: Side,
  val startLineIdx: Int,
  val side: Side,
  val lineIdx: Int,
)
