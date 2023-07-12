// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluableFeatureBase
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.step.SetupCompletionStep
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.openapi.project.Project


class TokenCompletionFeature : EvaluableFeatureBase<CompletionStrategy>("token-completion") {

  override fun getGenerateActionsProcessor(strategy: CompletionStrategy): GenerateActionsProcessor =
    CompletionGenerateActionsProcessor(strategy)


  override fun getActionsInvoker(project: Project, language: Language, strategy: CompletionStrategy): ActionsInvoker =
    CompletionActionsInvoker(project, language, strategy)

  override fun getStrategySerializer(): StrategySerializer<CompletionStrategy> = CompletionStrategySerializer()

  override fun getEvaluationSteps(language: Language, strategy: CompletionStrategy): List<EvaluationStep> =
    listOf(SetupCompletionStep(language.name, strategy.completionType))
}