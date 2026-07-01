// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.scopeChooser

import com.intellij.ide.util.scopeChooser.FrontendScopeChooser
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionListener
import javax.swing.JComponent

/**
 * Abstracts over the two different scope-chooser implementations:
 * - [ScopeChooserCombo] (monolith)
 * - [FrontendScopeChooser] (split)
 */
@ApiStatus.Internal
interface TodoScopeChooser {
  fun getSelectedScopeId(): String?
  fun getSelectedScopeName(): String?
  fun asComponent(): JComponent
  fun addSelectionListener(listener: Runnable)
}