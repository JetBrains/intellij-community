// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.util.turnOffDefaultTasksModel

import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

@Internal
class GradleTurnOffDefaultTasksModelBuilder : AbstractModelBuilderService() {

  override fun canBuild(modelName: String): Boolean =
    GradleTurnOffDefaultTasksRequest::class.java.name == modelName

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Nothing? {
    val startParameter = project.gradle.startParameter
    if (startParameter.taskNames.isEmpty()) {
      project.defaultTasks = listOf(":help")
      startParameter.setTaskNames(null)
      startParameter.setExcludedTaskNames(listOf(":help"))
    }
    return null
  }
}
