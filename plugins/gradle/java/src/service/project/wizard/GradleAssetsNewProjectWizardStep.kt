// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.GeneratorFile
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.getBuildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.getSettingsScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.settingsScript
import java.nio.file.Path
import kotlin.io.path.readText

abstract class GradleAssetsNewProjectWizardStep<ParentStep>(
  protected val parent: ParentStep,
) : AssetsNewProjectWizardStep(parent)
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData,
        ParentStep : GradleNewProjectWizardData,
        ParentStep : GradleNewProjectWizardStep<*> {

  override fun setupProject(project: Project) {
    super.setupProject(project)

    parent.setupProjectFromBuilder(project)
  }

  fun addBuildScript(configure: GradleBuildScriptBuilder<*>.() -> Unit) {
    val name = getBuildScriptName(parent.gradleDsl)
    val content = buildScript(parent.gradleVersionToUse, parent.gradleDsl, configure)
    addAssets(GeneratorFile(name, content))
  }

  fun addSettingsScript(configure: GradleSettingScriptBuilder<*>.() -> Unit) {
    val name = getSettingsScriptName(parent.gradleDsl)
    val content = settingsScript(parent.gradleVersionToUse, parent.gradleDsl, configure)
    addAssets(GeneratorFile(name, content))
  }

  fun addOrConfigureSettingsScript(configure: GradleSettingScriptBuilder<*>.() -> Unit = {}) {
    val parentData = parent.parentData
    if (parentData == null) {
      addSettingsScript {
        setProjectName(parent.artifactId)
        configure()
      }
    }
    else {
      val linkedProjectPath = Path.of(parentData.linkedExternalProjectPath)
      configureSettingsScript(linkedProjectPath) {
        include(linkedProjectPath.relativize(Path.of(parent.contentEntryPath)))
        configure()
      }
    }
  }

  private fun configureSettingsScript(projectPath: Path, configure: GradleSettingScriptBuilder<*>.() -> Unit) {
    if (configureSettingsScript(projectPath, GradleDsl.KOTLIN, configure)) return
    if (configureSettingsScript(projectPath, GradleDsl.GROOVY, configure)) return

    val name = getSettingsScriptName(parent.gradleDsl)
    val path = projectPath.resolve(name)
    val content = settingsScript(parent.gradleVersionToUse, parent.gradleDsl) {
      setProjectName(parent.artifactId)
      configure()
    }
    val relativePath = projectPath.relativize(path)
    addAssets(GeneratorFile(relativePath.toString(), content))
  }

  private fun configureSettingsScript(
    projectPath: Path,
    gradleDsl: GradleDsl,
    configure: GradleSettingScriptBuilder<*>.() -> Unit,
  ): Boolean {
    val name = getSettingsScriptName(gradleDsl)
    val path = projectPath.resolve(name)
    val oldContent = runCatching { path.readText() }.getOrNull() ?: return false
    val content = settingsScript(parent.gradleVersionToUse, gradleDsl) {
      addCode(oldContent)
      configure()
    }
    val relativePath = Path.of(parent.contentEntryPath).relativize(path)
    addAssets(GeneratorFile(relativePath.toString(), content))
    return true
  }
}