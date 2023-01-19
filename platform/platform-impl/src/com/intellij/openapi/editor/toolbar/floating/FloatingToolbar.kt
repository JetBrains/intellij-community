// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.observable.util.whenMouseMoved
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import javax.swing.JComponent

class FloatingToolbar(
  private val ownerComponent: JComponent,
  actionGroup: ActionGroup,
  private val parentDisposable: Disposable
) : AbstractFloatingToolbarComponent(actionGroup, parentDisposable) {

  override val autoHideable: Boolean = true

  override fun isComponentOnHold(): Boolean {
    return isComponentUnderMouse() || isFocusAncestor()
  }

  override fun installMouseMotionWatcher() {
    ownerComponent.whenMouseMoved(parentDisposable) {
      scheduleShow()
    }
  }

  init {
    init(ownerComponent)
  }
}