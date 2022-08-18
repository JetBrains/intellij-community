// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Factory for Project-level widgets placed in main toolbar.
 *
 * Extension point: `com.intellij.projectToolbarWidget`
 *
 * @see [MainToolbarWidgetFactory]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface MainToolbarProjectWidgetFactory : MainToolbarWidgetFactory {

  /**
   * Factory method to create widget
   *
   * @param project current project
   */
  fun createWidget(project: Project): JComponent
}