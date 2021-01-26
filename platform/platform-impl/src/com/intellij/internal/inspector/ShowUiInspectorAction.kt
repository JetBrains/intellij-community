// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction

/**
 * @author Konstantin Bulenkov
 */
class ShowUiInspectorAction: DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    UiInspectorAction.showUiInspectorForEvent(e.project, e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT))
  }
}