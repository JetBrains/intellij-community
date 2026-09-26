// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.scopeChooser

import com.intellij.ide.util.scopeChooser.FrontendScopeChooser
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionListener
import javax.swing.JComponent

@ApiStatus.Internal
class FrontendTodoScopeChooserImpl(private val chooser: FrontendScopeChooser) : TodoScopeChooser {
  override fun getSelectedScopeId(): String? = chooser.selectedScopeId

  override fun getSelectedScopeName(): String? = chooser.selectedScopeName

  override fun asComponent(): JComponent = chooser.component

  override fun addSelectionListener(listener: Runnable) {
    chooser.comboBox.addActionListener(ActionListener { listener.run() })
  }
}