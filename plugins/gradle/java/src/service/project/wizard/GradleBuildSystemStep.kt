// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.*
import com.intellij.util.lang.JavaVersion
import icons.GradleIcons
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.*
import javax.swing.Icon

abstract class GradleBuildSystemStep<ParentStep>(parent: ParentStep)
  : MavenizedNewProjectWizardStep<ProjectData, ParentStep>(parent)
  where ParentStep : com.intellij.ide.wizard.NewProjectWizardBaseData, ParentStep : com.intellij.ide.wizard.NewProjectWizardStep {

  abstract val sdkComboBox: com.intellij.ui.dsl.builder.Cell<JdkComboBox>
  abstract val sdk: Sdk?


  override fun createView(data: ProjectData) = GradleDataView(data)

  override fun setupUI(builder: Panel) {
    super.setupUI(builder)
    sdkComboBox.validationOnApply {
      sdk?.let { validateGradleVersion(it) }
    }
  }

  override fun findAllParents(): List<ProjectData> {
    val project = context.project ?: return emptyList()
    return ProjectDataManager.getInstance()
      .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
      .mapNotNull { it.externalProjectStructure }
      .map { it.data }
  }

  override fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
    if (artifactId.isEmpty()) {
      return error(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.missing.error",
        if (context.isCreatingNewProject) 1 else 0))
    }
    if (artifactId != parentStep.name) {
      return error(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.name.and.artifact.id.is.different.error",
        if (context.isCreatingNewProject) 1 else 0))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateGradleVersion(sdk: Sdk): ValidationInfo? {
    val javaVersion = getJavaVersion(sdk) ?: return null
    if (getGradleVersion(sdk) == null) {
      val preferredGradleVersion = getPreferredGradleVersion()
      val errorTitle = GradleBundle.message("gradle.settings.wizard.unsupported.jdk.title", if (context.isCreatingNewProject) 0 else 1)
      val errorMessage = GradleBundle.message(
        "gradle.settings.wizard.unsupported.jdk.message",
        javaVersion.toFeatureString(),
        MINIMUM_SUPPORTED_JAVA,
        MAXIMUM_SUPPORTED_JAVA,
        preferredGradleVersion.version)
      val dialog = MessageDialogBuilder.yesNo(errorTitle, errorMessage).asWarning()
      if (!dialog.ask(component)) {
        return error(errorTitle)
      }
    }
    return null
  }

  private fun getJavaVersion(sdk: Sdk?): JavaVersion? {
    val jdk = sdk ?: return null
    val versionString = jdk.versionString ?: return null
    return JavaVersion.tryParse(versionString)
  }

  private fun getPreferredGradleVersion(): GradleVersion {
    val project = context.project ?: return GradleVersion.current()
    return findGradleVersion(project) ?: GradleVersion.current()
  }

  private fun getGradleVersion(sdk: Sdk?): GradleVersion? {
    val preferredGradleVersion = getPreferredGradleVersion()
    val javaVersion = getJavaVersion(sdk) ?: return preferredGradleVersion
    return when (isSupported(preferredGradleVersion, javaVersion)) {
      true -> preferredGradleVersion
      else -> suggestGradleVersion(javaVersion)
    }
  }

  protected fun suggestGradleVersion(sdk: Sdk?): GradleVersion {
    return getGradleVersion(sdk) ?: getPreferredGradleVersion()
  }

  class GradleDataView(override val data: ProjectData) : DataView<ProjectData>() {
    override val location: String = data.linkedExternalProjectPath
    override val icon: Icon = GradleIcons.GradleFile
    override val presentationName: String = data.externalName
    override val groupId: String = data.group ?: ""
    override val version: String = data.version ?: ""
  }
}