// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * Extension point: com.intellij.projectToolbarWidget
 */
interface MainToolbarProjectWidgetFactory : MainToolbarWidgetFactory {
  fun createWidget(project: Project): JComponent
}