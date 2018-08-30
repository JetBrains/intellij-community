// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.widget

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidgetProvider

class EditorConfigStatusBarWidgetProvider : StatusBarWidgetProvider {
  override fun getWidget(project: Project) = EditorConfigStatusBarWidget(project)
}
