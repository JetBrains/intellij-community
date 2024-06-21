// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel

import com.intellij.gradle.toolingExtension.impl.model.taskIndex.GradleTaskIndex
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleObjectUtil
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalTask
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.builder.ProjectExtensionsDataBuilderImpl.Companion.getType

@ApiStatus.Internal
class GradleTaskModelBuilder : AbstractModelBuilderService() {

  override fun canBuild(modelName: String): Boolean {
    return modelName == GradleTaskModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): Any {
    val taskModel = DefaultGradleTaskModel()
    taskModel.tasks = getTasks(project, context)
    return taskModel
  }

  private fun getTasks(
    project: Project,
    context: ModelBuilderContext,
  ): Map<String, DefaultExternalTask> {
    val tasks = GradleTaskIndex.getInstance(context)
      .getAllTasks(project)

    val result = HashMap<String, DefaultExternalTask>()
    for (task in tasks) {
      val taskName = task.name
      val taskPath = task.path
      val projectPath = project.path
      val projectTaskPath = if (":" == projectPath) ":$taskName" else "$projectPath:$taskName"

      var externalTask = result[taskName]
      if (externalTask == null) {
        externalTask = DefaultExternalTask()
        externalTask.name = taskName
        externalTask.qName = taskName
        externalTask.description = task.description
        externalTask.group = GradleObjectUtil.notNull(task.group, "other")
        val isInternalTest = GradleTaskUtil.getBooleanProperty(task, "idea.internal.test", false)
        val isEffectiveTest = "check" == taskName && "verification" == task.group
        val isJvmTest = task is Test
        val isAbstractTest = task is AbstractTestTask
        externalTask.isTest = isJvmTest || isAbstractTest || isInternalTest || isEffectiveTest
        externalTask.isJvmTest = isJvmTest || isAbstractTest
        externalTask.type = getType(task)
        result[externalTask.name] = externalTask
      }

      if (projectTaskPath == taskPath) {
        externalTask.qName = taskPath
      }
    }
    return result
  }

  override fun reportErrorMessage(
    modelName: String,
    project: Project,
    context: ModelBuilderContext,
    exception: Exception,
  ) {
    context.messageReporter.createMessage()
      .withGroup(Messages.TASK_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Gradle task model building failure")
      .withException(exception)
      .reportMessage(project)
  }
}