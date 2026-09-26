// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SeEssentialTabFactory: SeTabFactory {
  val name: String
  val priority: Int

  /**
   * Tells if the tab can appear for [project].
   */
  fun isAvailable(project: Project?): Boolean = true
}
