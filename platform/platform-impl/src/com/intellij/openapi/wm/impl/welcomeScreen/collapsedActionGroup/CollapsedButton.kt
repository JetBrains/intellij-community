// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Crates [JComponent] that renders as collapsed button with text from [actionGroup] and width from the longest child action.
 */
@RequiresEdt
fun createCollapsedButton(actionGroup: CollapsedActionGroup, getActionWidth: (childAction: AnAction) -> Int): JComponent {
  val button = collapsedButton(actionGroup.templateText)

  val maxChildWidth = actionGroup.getChildren(ActionManager.getInstance()).maxOfOrNull { getActionWidth(it) }
  val preferredSize = button.preferredSize
  if (maxChildWidth != null && maxChildWidth > preferredSize.width) {
    button.preferredSize = Dimension(maxChildWidth, button.preferredSize.height)
  }
  return button
}

@RequiresEdt
private fun collapsedButton(@NlsContexts.BorderTitle text: String) = panel {
  collapsibleGroup(text) {

  }
}