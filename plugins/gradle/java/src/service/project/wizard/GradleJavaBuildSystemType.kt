// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.wizard.NewProjectStep
import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.layout.*
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.lang.JavaVersion
import icons.GradleIcons
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.wizard.AbstractGradleModuleBuilder.getBuildScriptData
import org.jetbrains.plugins.gradle.util.*
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer


class GradleJavaBuildSystemType : JavaBuildSystemType {
  override val name = "Gradle"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(context: WizardContext) : NewProjectWizardStep(context) {
    private val parentProperty = propertyGraph.graphProperty(::suggestParentByPath)
    private val groupIdProperty = propertyGraph.graphProperty(::suggestGroupIdByParent)
    private val artifactIdProperty = propertyGraph.graphProperty(::suggestArtifactIdByName)
    private val versionProperty = propertyGraph.graphProperty(::suggestVersionByParent)

    private var parent by parentProperty
    private var groupId by groupIdProperty.map { it.trim() }
    private var artifactId by artifactIdProperty.map { it.trim() }
    private var version by versionProperty.map { it.trim() }

    private val parents by lazy { findAllParents().map(::GradleDataView) }
    private var parentData: ProjectData?
      get() = DataView.getData(parent)
      set(value) {
        parent = if (value == null) EMPTY_VIEW else GradleDataView(value)
      }

    private fun findAllParents(): List<ProjectData> {
      val project = context.project ?: return emptyList()
      return ProjectDataManager.getInstance()
        .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        .mapNotNull { it.externalProjectStructure }
        .map { it.data }
    }

    private fun suggestParentByPath(): DataView<ProjectData> {
      val path = NewProjectStep.getPath(context)
      return parents.find { FileUtil.isAncestor(it.location, path.systemIndependentPath, true) } ?: EMPTY_VIEW
    }

    private fun suggestGroupIdByParent(): String {
      return parent.groupId
    }

    private fun suggestArtifactIdByName(): String {
      return NewProjectStep.getName(context)
    }

    private fun suggestVersionByParent(): String {
      return parent.version
    }

    private fun suggestNameByArtifactId(): String {
      return artifactId
    }

    private fun suggestLocationByParent(): String {
      return if (parent.isPresent) parent.location else context.projectFileDirectory
    }

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        JavaNewProjectWizard.SdkStep.getSdkComboBox(context)
          .withValidationOnApply { validateGradleVersion() }
        if (!context.isCreatingNewProject) {
          row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.parent.label")) {
            val presentationName = Function<DataView<ProjectData>, String> { it.presentationName }
            val parentComboBoxModel = SortedComboBoxModel(Comparator.comparing(presentationName, String.CASE_INSENSITIVE_ORDER))
            parentComboBoxModel.add(EMPTY_VIEW)
            parentComboBoxModel.addAll(parents)
            comboBox(parentComboBoxModel, parentProperty, renderer = getParentRenderer())
          }.largeGapAfter()
        }
        hideableRow(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.coordinates.title")) {
          row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.label")) {
            textField(groupIdProperty)
          }
          row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.label")) {
            textField(artifactIdProperty)
              .withValidationOnApply { validateArtifactId() }
              .withValidationOnInput { validateArtifactId() }
          }
          row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.label")) {
            textField(versionProperty)
          }
        }.largeGapAfter()
      }
    }

    private fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
      if (artifactId.isEmpty()) {
        return error(GradleBundle.message("gradle.structure.wizard.artifact.id.missing.error", if (context.isCreatingNewProject) 1 else 0))
      }
      if (artifactId != NewProjectStep.getName(context)) {
        return error(GradleBundle.message("gradle.structure.wizard.name.and.artifact.id.is.different.error",
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
      val jdk = JavaNewProjectWizard.SdkStep.getSdk(context) ?: return null
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

    private fun getParentRenderer(): ListCellRenderer<DataView<ProjectData>?> {
      return object : SimpleListCellRenderer<DataView<ProjectData>?>() {
        override fun customize(list: JList<out DataView<ProjectData>?>,
                               value: DataView<ProjectData>?,
                               index: Int,
                               selected: Boolean,
                               hasFocus: Boolean) {
          val view = value ?: EMPTY_VIEW
          text = view.presentationName
          icon = DataView.getIcon(view)
        }
      }
    }

    override fun setupProject(project: Project) {
      val builder = InternalGradleModuleBuilder().apply {
        isCreatingNewProject = true
        moduleJdk = JavaNewProjectWizard.SdkStep.getSdk(context)

        parentProject = parentData
        projectId = ProjectId(groupId, artifactId, version)
        isInheritGroupId = parentData?.group == groupId
        isInheritVersion = parentData?.version == version

        isUseKotlinDsl = false

        gradleVersion = suggestGradleVersion()
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

    init {
      val nameProperty = NewProjectStep.getNameProperty(context)
      val pathProperty = NewProjectStep.getPathProperty(context)

      nameProperty.dependsOn(artifactIdProperty, ::suggestNameByArtifactId)
      parentProperty.dependsOn(pathProperty, ::suggestParentByPath)
      pathProperty.dependsOn(parentProperty, ::suggestLocationByParent)
      groupIdProperty.dependsOn(parentProperty, ::suggestGroupIdByParent)
      artifactIdProperty.dependsOn(nameProperty, ::suggestArtifactIdByName)
      versionProperty.dependsOn(parentProperty, ::suggestVersionByParent)
    }

    class GradleDataView(override val data: ProjectData) : DataView<ProjectData>() {
      override val location: String = data.linkedExternalProjectPath
      override val icon: Icon = GradleIcons.GradleFile
      override val presentationName: String = data.externalName
      override val groupId: String = data.group ?: ""
      override val version: String = data.version ?: ""
    }

    companion object {
      val EMPTY_VIEW = object : DataView<Nothing>() {
        override val data: Nothing get() = throw UnsupportedOperationException()
        override val location: String = ""
        override val icon: Nothing get() = throw UnsupportedOperationException()
        override val presentationName: String = "<None>"
        override val groupId: String = "org.example"
        override val version: String = "1.0-SNAPSHOT"

        override val isPresent: Boolean = false
      }
    }
  }
}
