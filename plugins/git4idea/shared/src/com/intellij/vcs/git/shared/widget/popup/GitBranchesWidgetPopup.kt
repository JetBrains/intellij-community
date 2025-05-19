// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget.popup

import org.jetbrains.annotations.ApiStatus

/**
 * Temporary workaround to add [GitBranchesTreePopupBase] in data context before moving it
 * to the shared module.
 */
@ApiStatus.Internal
interface GitBranchesWidgetPopup {
  val userResized: Boolean

  fun restoreDefaultSize()

  fun setGroupingByPrefix(groupByPrefix: Boolean)
}