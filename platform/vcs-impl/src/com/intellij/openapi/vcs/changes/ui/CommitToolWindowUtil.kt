// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.wm.ToolWindowType

internal object CommitToolWindowUtil {
  fun isInWindow(twType: ToolWindowType): Boolean {
    return twType == ToolWindowType.WINDOWED || twType == ToolWindowType.FLOATING
  }
}