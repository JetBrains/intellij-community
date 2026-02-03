// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarComponent
import javax.swing.JComponent

class LayoutActionsFloatingToolbar(
  parentComponent: JComponent,
  actionGroup: ActionGroup,
  parentDisposable: Disposable,
) : AbstractFloatingToolbarComponent(
  actionGroup,
  parentComponent,
  parentDisposable
)
