// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.scopeChooser

import com.intellij.ide.util.scopeChooser.FrontendScopeChooser
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionListener
import javax.swing.JComponent

@ApiStatus.Internal
class FrontendTodoScopeChooserImpl(private val chooser: FrontendScopeChooser) : TodoScopeChooser {
  override fun getSelectedScopeId(): String? = chooser.getSelectedScopeId()

  override fun getSelectedScopeName(): String? = chooser.getSelectedScopeName()

  override fun asComponent(): JComponent = chooser

  override fun addSelectionListener(listener: Runnable) {
    chooser.getComboBox().addActionListener(ActionListener { listener.run() })
  }
}