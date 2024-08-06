// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.ml.llm.MLLlmIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys

class GHPRAIReviewOpenAction : AnAction("Open Review Buddy", null, MLLlmIcons.AiAssistantColored) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    e.project ?: return
    e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER) ?: return
    e.getData(GHPRActionKeys.PULL_REQUEST_REPOSITORY) ?: return

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dataProvider = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER) ?: return
    val gitRepository = e.getData(GHPRActionKeys.PULL_REQUEST_REPOSITORY) ?: return

    project.service<GHPRAIReviewToolwindowViewModel>()
      .requestReview(dataProvider, gitRepository)
  }
}