// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import java.awt.event.ActionListener
import javax.swing.event.ChangeListener

interface ToolbarSplitButtonModel {

  fun isActionButtonSelected(): Boolean

  fun setActionButtonSelected(b: Boolean)

  fun isExpandButtonSelected(): Boolean

  fun setExpandButtonSelected(b: Boolean)

  fun addActionListener(listener: ActionListener)

  fun removeActionListener(listener: ActionListener)

  fun addExpandListener(listener: ActionListener)

  fun removeExpandListener(listener: ActionListener)

  fun getActionListeners(): List<ActionListener>

  fun getExpandListeners(): List<ActionListener>

  fun addChangeListener(l: ChangeListener)

  fun removeChangeListener(l: ChangeListener)
}