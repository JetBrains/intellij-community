// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.SettingsEditorFragmentContainer
import com.intellij.openapi.externalSystem.service.execution.configuration.fragments.addTag
import com.intellij.openapi.externalSystem.service.ui.project.path.ExternalSystemWorkingDirectoryInfo
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

class GradleRunConfigurationExtension
  : ExternalSystemReifiedRunConfigurationExtension<GradleRunConfiguration>(GradleRunConfiguration::class.java) {

  override fun SettingsEditorFragmentContainer<GradleRunConfiguration>.configureFragments(configuration: GradleRunConfiguration) {
    val project = configuration.project
    addBeforeRunFragment(GradleBeforeRunTaskProvider.ID)
    val workingDirectoryField = addWorkingDirectoryFragment(project).component().component
    addCommandLineFragment(project, workingDirectoryField)
    addTag(
      "gradle.tasks.script.debugging.fragment",
      GradleBundle.message("gradle.tasks.script.debugging"),
      GradleBundle.message("gradle.settings.title"),
      null,
      GradleRunConfiguration::isDebugServerProcess,
      GradleRunConfiguration::setDebugServerProcess
    )
    addTag(
      "gradle.tasks.reattach.debug.process.fragment",
      GradleBundle.message("gradle.tasks.reattach.debug.process"),
      GradleBundle.message("gradle.settings.title"),
      GradleBundle.message("gradle.tasks.reattach.debug.process.comment"),
      { !isReattachDebugProcess },
      { isReattachDebugProcess = !it }
    )
    addTag(
      "gradle.tasks.debugging.all.fragment",
      GradleBundle.message("gradle.tasks.debugging.all"),
      GradleBundle.message("gradle.settings.title"),
      GradleBundle.message("gradle.tasks.debugging.all.comment"),
      GradleRunConfiguration::isDebugAllEnabled,
      GradleRunConfiguration::setDebugAllEnabled
    )
    addTag(
      "gradle.tasks.run_as_test.fragment",
      GradleBundle.message("gradle.tasks.tests.force"),
      GradleBundle.message("gradle.settings.title"),
      GradleBundle.message("gradle.tasks.tests.force.comment"),
      GradleRunConfiguration::isRunAsTest,
      GradleRunConfiguration::setRunAsTest
    )
  }

  private fun SettingsEditorFragmentContainer<GradleRunConfiguration>.addWorkingDirectoryFragment(
    project: Project
  ) = addWorkingDirectoryFragment(
    project,
    ExternalSystemWorkingDirectoryInfo(project, SYSTEM_ID)
  )

  private fun SettingsEditorFragmentContainer<GradleRunConfiguration>.addCommandLineFragment(
    project: Project,
    workingDirectoryField: WorkingDirectoryField
  ) = addCommandLineFragment(
    project,
    GradleCommandLineInfo(project, workingDirectoryField),
    { rawCommandLine },
    { rawCommandLine = it }
  )
}
