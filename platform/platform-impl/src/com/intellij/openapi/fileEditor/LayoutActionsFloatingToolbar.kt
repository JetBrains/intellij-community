// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor


import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import javax.swing.JComponent

class LayoutActionsFloatingToolbar(
  parentComponent: JComponent,
  actionGroup: ActionGroup
) : AbstractFloatingToolbarComponent(actionGroup, false) {

  init {
    init(parentComponent)
  }
}
