// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectWidgetActionsFilter {
  fun shouldHideProjectSwitchingActions(event: AnActionEvent): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<ProjectWidgetActionsFilter> =
      ExtensionPointName.create("com.intellij.projectWidgetActionsFilter")
  }
}
