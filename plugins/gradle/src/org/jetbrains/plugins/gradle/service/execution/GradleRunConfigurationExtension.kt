// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtension
import org.jetbrains.plugins.gradle.execution.GradleBeforeRunTaskProvider
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleRunConfigurationExtension : ExternalSystemRunConfigurationExtension() {
  override fun isApplicableFor(configuration: ExternalSystemRunConfiguration): Boolean {
    return configuration is GradleRunConfiguration
  }

  @Suppress("UNCHECKED_CAST")
  override fun <P : ExternalSystemRunConfiguration> createFragments(configuration: P): List<SettingsEditorFragment<P, *>> {
    val fragments = ArrayList<SettingsEditorFragment<GradleRunConfiguration, *>>()
    fragments.add(createBeforeRun(GradleBeforeRunTaskProvider.ID))
    fragments.add(createScriptDebugEnabledTag())
    fragments.add(createReattachDebugProcessTag())
    fragments.add(createDebugAllEnabledTag())
    return fragments as List<SettingsEditorFragment<P, *>>
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
