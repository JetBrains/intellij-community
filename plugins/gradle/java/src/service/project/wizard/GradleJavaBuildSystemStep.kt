// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.*
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.lang.JavaVersion
import icons.GradleIcons
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.*
import javax.swing.Icon

class GradleJavaBuildSystemStep(
  parent: JavaNewProjectWizard.Step
) : MavenizedNewProjectWizardStep<ProjectData, JavaNewProjectWizard.Step>(parent) {

  override fun createView(data: ProjectData) = GradleDataView(data)

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

  override fun setupUI(builder: Panel) {
    super.setupUI(builder)

    parentStep.sdkComboBox
      .validationOnApply { validateGradleVersion() }
  }

  private fun ValidationInfoBuilder.validateGradleVersion(): ValidationInfo? {
    val javaVersion = getJavaVersion() ?: return null
    if (getGradleVersion() == null) {
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

  private fun getJavaVersion(): JavaVersion? {
    val jdk = parentStep.sdk ?: return null
    val versionString = jdk.versionString ?: return null
    return JavaVersion.tryParse(versionString)
  }

  private fun getPreferredGradleVersion(): GradleVersion {
    val project = context.project ?: return GradleVersion.current()
    return findGradleVersion(project) ?: GradleVersion.current()
  }

  private fun getGradleVersion(): GradleVersion? {
    val preferredGradleVersion = getPreferredGradleVersion()
    val javaVersion = getJavaVersion() ?: return preferredGradleVersion
    return when (isSupported(preferredGradleVersion, javaVersion)) {
      true -> preferredGradleVersion
      else -> suggestGradleVersion(javaVersion)
    }
  }

  private fun suggestGradleVersion(): GradleVersion {
    return getGradleVersion() ?: getPreferredGradleVersion()
  }

  override fun setupProject(project: Project) {
    val builder = InternalGradleModuleBuilder().apply {
      moduleJdk = parentStep.sdk
      name = parentStep.name
      contentEntryPath = parentStep.projectPath.systemIndependentPath

      isCreatingNewProject = context.isCreatingNewProject

      parentProject = parentData
      projectId = ProjectId(groupId, artifactId, version)
      isInheritGroupId = parentData?.group == groupId
      isInheritVersion = parentData?.version == version

      isUseKotlinDsl = false

      gradleVersion = suggestGradleVersion()
    }

    builder.addModuleConfigurationUpdater(object : ModuleBuilder.ModuleConfigurationUpdater() {
      override fun update(module: Module, rootModel: ModifiableRootModel) {
        AbstractGradleModuleBuilder.getBuildScriptData(module)
          ?.withJavaPlugin()
          ?.withJUnit()
      }
    })

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    builder.commit(project)
  }

  class GradleDataView(override val data: ProjectData) : DataView<ProjectData>() {
    override val location: String = data.linkedExternalProjectPath
    override val icon: Icon = GradleIcons.GradleFile
    override val presentationName: String = data.externalName
    override val groupId: String = data.group ?: ""
    override val version: String = data.version ?: ""
  }

  class Factory : JavaBuildSystemType {
    override val name = GradleConstants.SYSTEM_ID.readableName

    override fun createStep(parent: JavaNewProjectWizard.Step) = GradleJavaBuildSystemStep(parent)
  }
}