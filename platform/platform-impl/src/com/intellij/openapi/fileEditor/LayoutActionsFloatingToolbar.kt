// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import javax.swing.JComponent

class LayoutActionsFloatingToolbar : AbstractFloatingToolbarComponent {
  constructor(
    parentComponent: JComponent,
    actionGroup: ActionGroup,
    parentDisposable: Disposable
  ) : super(actionGroup, parentDisposable) {
    init(parentComponent)
  }

  override val autoHideable: Boolean = false

  override fun isComponentOnHold(): Boolean = true

  override fun installMouseMotionWatcher(): Unit = Unit
}
