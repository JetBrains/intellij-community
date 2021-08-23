// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaSettings
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.ui.layout.*
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.wizard.AbstractGradleModuleBuilder.getBuildScriptData
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.suggestGradleVersion


class GradleJavaBuildSystemType : JavaBuildSystemType<GradleJavaBuildSystemSettings>("Gradle") {
  override val settingsKey = GradleJavaBuildSystemSettings.KEY
  override fun createSettings() = GradleJavaBuildSystemSettings()

  override fun setupProject(project: Project, settings: GradleJavaBuildSystemSettings, context: WizardContext) {
    val languageSettings = JavaSettings.KEY.get(context)

    val builder = InternalGradleModuleBuilder().apply {
      isCreatingNewProject = true
      moduleJdk = languageSettings.sdk

      parentProject = null
      projectId = ProjectId(settings.groupId, settings.artifactId, null)
      isInheritGroupId = false
      isInheritVersion = false

      isUseKotlinDsl = false

      gradleVersion = suggestGradleVersion(languageSettings) ?: GradleVersion.current()
    }

    builder.addModuleConfigurationUpdater(object : ModuleBuilder.ModuleConfigurationUpdater() {
      override fun update(module: Module, rootModel: ModifiableRootModel) {
        getBuildScriptData(module)
          ?.withJavaPlugin()
          ?.withJUnit()
      }
    })

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    builder.commit(project)
  }

  private fun suggestGradleVersion(languageSettings: JavaSettings): GradleVersion? {
    val jdk = languageSettings.sdk ?: return null
    val versionString = jdk.versionString ?: return null
    val javaVersion = JavaVersion.tryParse(versionString) ?: return null
    return suggestGradleVersion(javaVersion)
  }

  override fun advancedSettings(settings: GradleJavaBuildSystemSettings, context: WizardContext) = panel {
    hideableRow(GradleBundle.message("label.project.wizard.new.project.advanced.settings.title")) {
      row(GradleBundle.message("label.project.wizard.new.project.group.id")) {
        textField(settings::groupId)
      }
      row(GradleBundle.message("label.project.wizard.new.project.artifact.id")) {
        textField(settings::artifactId)
      }
    }.largeGapAfter()
  }
}
