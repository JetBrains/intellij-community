// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.observable.util.whenMouseMoved
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import javax.swing.JComponent

class FloatingToolbar(
  ownerComponent: JComponent,
  actionGroup: ActionGroup,
  parentDisposable: Disposable,
) : AbstractFloatingToolbarComponent(
  actionGroup,
  ownerComponent,
  parentDisposable
) {

  override fun isComponentOnHold(): Boolean {
    return isComponentUnderMouse() || isFocusAncestor()
  }

  init {
    autoHideable = true
  }

  init {
    ownerComponent.whenMouseMoved(parentDisposable) {
      scheduleShow()
    }
  }
}