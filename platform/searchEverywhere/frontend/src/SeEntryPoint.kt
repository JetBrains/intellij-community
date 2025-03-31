// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.actions.SearchEverywhereEntryPoint
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeEntryPoint : SearchEverywhereEntryPoint {
  override fun isAvailable(e: AnActionEvent): Boolean {
    return SeFrontendService.isEnabled
  }

  override fun initiateSearchPopup(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    SeFrontendService.getInstance(project).showPopup(null, e)
    return true
  }
}