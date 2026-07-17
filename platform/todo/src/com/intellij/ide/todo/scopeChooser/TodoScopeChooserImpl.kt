// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.scopeChooser

import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionListener
import javax.swing.JComponent

@ApiStatus.Internal
class TodoScopeChooserImpl(private val combo: ScopeChooserCombo) : TodoScopeChooser {
  override fun getSelectedScopeId(): String? = combo.selectedScopeId

  override fun getSelectedScopeName(): String? = combo.selectedScopeName

  override fun asComponent(): JComponent = combo

  override fun addSelectionListener(listener: Runnable) {
    combo.childComponent.addActionListener(ActionListener { listener.run() })
  }
}