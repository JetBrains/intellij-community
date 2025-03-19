// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Factory interface for creating a SpotlightPainter object.
 */
@ApiStatus.Internal
interface SpotlightPainterFactory {
  fun createSpotlightPainter(
    project: Project,
    target: JComponent,
    parent: Disposable,
    updater: (SpotlightPainter) -> Unit,
  ): SpotlightPainter
}
