// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemWorkingDirectoryInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

class GradleRunConfigurationExtension
  : ExternalSystemReifiedRunConfigurationExtension<GradleRunConfiguration>(GradleRunConfiguration::class.java) {

  override fun SettingsFragmentsContainer<GradleRunConfiguration>.configureFragments(configuration: GradleRunConfiguration) {
    val project = configuration.project
    addBeforeRunFragment(GradleBeforeRunTaskProvider.ID)
    val workingDirectoryField = addWorkingDirectoryFragment(project).component().component
    addCommandLineFragment(project, workingDirectoryField)
    addTag(
      "gradle.tasks.script.debugging.fragment",
      GradleBundle.message("gradle.tasks.script.debugging"),
      GradleBundle.message("gradle.settings.title.debug"),
      null,
      GradleRunConfiguration::isScriptDebugEnabled,
      GradleRunConfiguration::setScriptDebugEnabled
    )
    addTag(
      "gradle.tasks.reattach.debug.process.fragment",
      GradleBundle.message("gradle.tasks.reattach.debug.process"),
      GradleBundle.message("gradle.settings.title.debug"),
      GradleBundle.message("gradle.tasks.reattach.debug.process.comment"),
      GradleRunConfiguration::isReattachDebugProcess,
      GradleRunConfiguration::setReattachDebugProcess
    )
    addTag(
      "gradle.tasks.debugging.all.fragment",
      GradleBundle.message("gradle.tasks.debugging.all"),
      GradleBundle.message("gradle.settings.title.debug"),
      GradleBundle.message("gradle.tasks.debugging.all.comment"),
      GradleRunConfiguration::isDebugAllEnabled,
      GradleRunConfiguration::setDebugAllEnabled
    )
  }

  private fun SettingsFragmentsContainer<GradleRunConfiguration>.addWorkingDirectoryFragment(
    project: Project
  ) = addWorkingDirectoryFragment(
    project,
    ExternalSystemWorkingDirectoryInfo(project, SYSTEM_ID)
  )

  private fun SettingsFragmentsContainer<GradleRunConfiguration>.addCommandLineFragment(
    project: Project,
    workingDirectoryField: WorkingDirectoryField
  ) = addCommandLineFragment(
    project,
    GradleCommandLineInfo(project, workingDirectoryField),
    { rawCommandLine },
    { rawCommandLine = it }
  )
}
