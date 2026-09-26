// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.tools.fragmented.UnifiedDiffChange
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ChangeListDiffViewerDecorator {
  fun initialize(viewer: TwosideTextDiffViewer)
  fun decorateFragments(toggleableLineRanges: List<LocalTrackerDiffUtil.ToggleableLineRange>, viewer: TwosideTextDiffViewer)
  fun isAvailable(viewer: TwosideTextDiffViewer): Boolean
  fun initialize(viewer: UnifiedDiffViewer)
  fun decorateFragments(changes: List<UnifiedDiffChange>, viewer: UnifiedDiffViewer)
  fun isAvailable(viewer: UnifiedDiffViewer): Boolean
  companion object {
    val EP_NAME: ExtensionPointName<ChangeListDiffViewerDecorator> =
      ExtensionPointName<ChangeListDiffViewerDecorator>("com.intellij.vcs.impl.changeListDiffViewerDecorator")
  }

}
