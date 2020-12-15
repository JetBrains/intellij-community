package com.intellij.completion.ml.local.actions

import com.intellij.completion.ml.local.CompletionRankingLocalBundle
import com.intellij.completion.ml.local.models.LocalModelsBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TrainLocalModelsAction : AnAction(CompletionRankingLocalBundle.message("ml.completion.local.models.training.action")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (LocalModelsBuilder.isTraining()) {
      //TODO: Show message that model is training right now
      return
    }
    LocalModelsBuilder.train(project)
    //TODO: Show message that model trained successfully
  }
}