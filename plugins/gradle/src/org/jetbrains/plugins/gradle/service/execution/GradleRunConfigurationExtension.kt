// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemProjectPathInfoImpl
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleRunConfigurationExtension
  : ExternalSystemReifiedRunConfigurationExtension<GradleRunConfiguration>(GradleRunConfiguration::class.java) {

  override fun MutableList<SettingsEditorFragment<GradleRunConfiguration, *>>.configureFragments(configuration: GradleRunConfiguration) {
    val project = configuration.project
    val projectPathInfo = ExternalSystemProjectPathInfoImpl(project, GradleConstants.SYSTEM_ID)
    val projectPath = createProjectPath<GradleRunConfiguration>(project, projectPathInfo)
    val tasksAndArgumentsInfo = GradleTasksAndArgumentsInfo(project, projectPath.component().component)
    val tasksAndArguments = createTasksAndArguments<GradleRunConfiguration>(project, tasksAndArgumentsInfo)

    add(createBeforeRun(GradleBeforeRunTaskProvider.ID))
    add(tasksAndArguments)
    add(projectPath)
    add(createScriptDebugEnabledTag())
    add(createReattachDebugProcessTag())
    add(createDebugAllEnabledTag())
  }

  private fun createScriptDebugEnabledTag() = createSettingsTag(
    "gradle.tasks.script.debugging.fragment",
    GradleBundle.message("gradle.tasks.script.debugging"),
    GradleBundle.message("gradle.settings.title.debug"),
    null,
    GradleRunConfiguration::isScriptDebugEnabled,
    GradleRunConfiguration::setScriptDebugEnabled,
    200
  )

  private fun createReattachDebugProcessTag() = createSettingsTag(
    "gradle.tasks.reattach.debug.process.fragment",
    GradleBundle.message("gradle.tasks.reattach.debug.process"),
    GradleBundle.message("gradle.settings.title.debug"),
    GradleBundle.message("gradle.tasks.reattach.debug.process.comment"),
    GradleRunConfiguration::isReattachDebugProcess,
    GradleRunConfiguration::setReattachDebugProcess,
    200
  )

  private fun createDebugAllEnabledTag() = createSettingsTag(
    "gradle.tasks.debugging.all.fragment",
    GradleBundle.message("gradle.tasks.debugging.all"),
    GradleBundle.message("gradle.settings.title.debug"),
    GradleBundle.message("gradle.tasks.debugging.all.comment"),
    GradleRunConfiguration::isDebugAllEnabled,
    GradleRunConfiguration::setDebugAllEnabled,
    200
  )

}
