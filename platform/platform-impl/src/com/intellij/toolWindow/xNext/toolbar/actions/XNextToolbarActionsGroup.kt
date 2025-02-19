// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class XNextToolbarActionsGroup : ActionGroup(), DumbAware {
  private val arrayOf = arrayListOf<AnAction>(ActionManager.getInstance().getAction("SearchEverywhere"), ActionManager.getInstance().getAction("MiniAiChat.Toggle"))

  init {
    ActionManager.getInstance().getAction("HideAllWindows")?.let {
      arrayOf.add(object : AnActionWrapper(it) {
        init {
          templatePresentation.icon = AllIcons.General.FitContent
        }
      })
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<out AnAction?> = arrayOf.toTypedArray()
}