// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.data

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal interface XNextToolbarManager {
  companion object {
    fun getInstance(project: Project): XNextToolbarManager = project.service()
  }

  val xNextToolbarState: XNextToolbarState

  fun isPinned(id: String): Boolean {
    return xNextToolbarState.pinned.contains(id)
  }

  fun updatePinned(linkSet: LinkedHashSet<String>)

  fun setPinned(id: String, pinned: Boolean)
}