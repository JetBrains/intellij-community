// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.*
import com.intellij.util.lang.JavaVersion
import icons.GradleIcons
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.*
import javax.swing.Icon

abstract class GradleNewProjectWizardStep<ParentStep>(parent: ParentStep) :
  MavenizedNewProjectWizardStep<ProjectData, ParentStep>(parent), GradleNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  final override val sdkProperty = propertyGraph.property<Sdk?>(null)
  final override val useKotlinDslProperty = propertyGraph.property(false)

  final override var sdk by sdkProperty
  final override var useKotlinDsl by useKotlinDslProperty

  override fun createView(data: ProjectData) = GradleDataView(data)

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
        val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
        sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
          .validationOnApply { validateGradleVersion() }
          .columns(COLUMNS_MEDIUM)
      }.bottomGap(BottomGap.SMALL)
      row(GradleBundle.message("gradle.dsl.new.project.wizard")) {
        segmentedButton(listOf(false, true)) {
          when (it) {
            true -> GradleBundle.message("gradle.dsl.new.project.wizard.kotlin")
            else -> GradleBundle.message("gradle.dsl.new.project.wizard.groovy")
          }
        }.bind(useKotlinDslProperty)
      }.bottomGap(BottomGap.SMALL)
    }
    super.setupUI(builder)
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
    val jdk = sdk ?: return null
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

  protected fun suggestGradleVersion(): GradleVersion {
    return getGradleVersion() ?: getPreferredGradleVersion()
  }

  class GradleDataView(override val data: ProjectData) : DataView<ProjectData>() {
    override val location: String = data.linkedExternalProjectPath
    override val icon: Icon = GradleIcons.GradleFile
    override val presentationName: String = data.externalName
    override val groupId: String = data.group ?: ""
    override val version: String = data.version ?: ""
  }
}