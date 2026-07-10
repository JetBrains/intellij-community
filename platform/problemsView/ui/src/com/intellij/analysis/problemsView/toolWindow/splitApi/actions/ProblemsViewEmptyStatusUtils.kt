// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi.actions

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewBundle
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.ActionEvent

private val ACTION_IDS: List<String> = listOf("CompileDirty", "InspectCode")

@ApiStatus.Internal
fun setupEmptyStatusActions(status: StatusText, panel: Component) {
  if (!Registry.`is`("ide.problems.view.empty.status.actions")) return

  val or = ProblemsViewBundle.message("problems.view.project.empty.or")
  var index = 0
  for (id in ACTION_IDS) {
    val action = ActionUtil.getAction(id) ?: continue
    val text = action.templateText
    if (text.isNullOrBlank()) continue
    if (index == 0) {
      status.appendText(".")
      status.appendLine("")
    }
    else {
      status.appendText(" ").appendText(or).appendText(" ")
    }
    status.appendText(text, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
    ) { event: ActionEvent? ->
      ActionUtil.invokeAction(action, panel, "ProblemsView", null, null)
    }
    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(action)
    if (!shortcut.isBlank()) status.appendText(" (").appendText(shortcut).appendText(")")
    index++
  }
}
