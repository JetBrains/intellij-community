// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import icons.GradleIcons
import org.jetbrains.plugins.gradle.util.GradleBundle.message
import java.util.function.Supplier

class GradleRuntimeType : LanguageRuntimeType<GradleRuntimeTargetConfiguration>(TYPE_ID) {
  override val icon = GradleIcons.Gradle

  override val displayName = "Gradle"

  override val configurableDescription = message("gradle.target.configure.label")

  override val launchDescription = message("gradle.target.run.label")

  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings) = true

  override fun createDefaultConfig() = GradleRuntimeTargetConfiguration()

  override fun createSerializer(config: GradleRuntimeTargetConfiguration): PersistentStateComponent<*> = config

  override fun createConfigurable(project: Project,
                                  config: GradleRuntimeTargetConfiguration,
                                  targetEnvironmentType: TargetEnvironmentType<*>,
                                  targetSupplier: Supplier<TargetEnvironmentConfiguration>): Configurable {
    return GradleRuntimeTargetUI(config, targetEnvironmentType, targetSupplier, project)
  }

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): GradleRuntimeTargetConfiguration? {
    return target.runtimes.findByType()
  }

  override fun duplicateConfig(config: GradleRuntimeTargetConfiguration): GradleRuntimeTargetConfiguration =
    duplicatePersistentComponent(this, config)

  companion object {
    @JvmStatic
    val TYPE_ID = "GradleRuntime"

    @JvmStatic
    val PROJECT_FOLDER_VOLUME = VolumeDescriptor(GradleRuntimeType::class.qualifiedName + ":projectFolder",
                                                 message("gradle.target.execution.project.folder.label"),
                                                 message("gradle.target.execution.project.folder.description"),
                                                 message("gradle.target.execution.project.folder.title"),
                                                 "")
  }
}