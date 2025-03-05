// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
interface ShelveSilentlyGotItTooltipProvider {
  @RequiresEdt
  fun showGotItTooltip(project: Project, contextComponent: Component): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<ShelveSilentlyGotItTooltipProvider> =
      ExtensionPointName("com.intellij.vcs.shelveSilentlyGotItTooltipProvider")

    @JvmStatic
    fun showGotItTooltip(project: Project, contextComponent: Component) {
      EP_NAME.findFirstSafe { it.showGotItTooltip(project, contextComponent) }
    }
  }
}