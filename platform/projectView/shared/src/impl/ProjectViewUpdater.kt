// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.platform.projectView.pane.ProjectViewPaneModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewUpdater {
  suspend fun continuouslyUpdatePane(pane: ProjectViewPaneModel)
}
