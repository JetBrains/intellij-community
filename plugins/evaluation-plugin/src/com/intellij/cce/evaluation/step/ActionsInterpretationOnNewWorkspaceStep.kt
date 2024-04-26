// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.ActionsInterpretationHandler
import com.intellij.cce.interpreter.InvokersFactory
import com.intellij.cce.workspace.Config
import com.intellij.openapi.project.Project

class ActionsInterpretationOnNewWorkspaceStep(config: Config,
                                              invokersFactory: InvokersFactory,
                                              project: Project) :
  CreateWorkspaceStep(
    config,
    ActionsInterpretationHandler(config, config.language, invokersFactory, project),
    project) {

  override val name: String = "Actions interpreting"

  override val description: String = "Interpretation of generated actions"
}