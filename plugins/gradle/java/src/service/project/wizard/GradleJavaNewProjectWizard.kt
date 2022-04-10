// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logArtifactIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logDslChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logGroupIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logVersionChanged
import com.intellij.ide.projectWizard.generators.*
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.addSampleCode
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.groupId
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = GradleConstants.SYSTEM_ID.readableName

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    GradleNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    GradleJavaNewProjectWizardData {

    override val addSampleCodeProperty = propertyGraph.property(false)
      .bindBooleanStorage("NewProjectWizard.addSampleCodeState")

    override var addSampleCode by addSampleCodeProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      builder.row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
      }.topGap(TopGap.SMALL)
    }

    override fun setupProject(project: Project) {
      val builder = generateModuleBuilder()
      builder.gradleVersion = suggestGradleVersion()

      builder.configureBuildScript {
        it.withJavaPlugin()
        it.withJUnit()
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)

      logSdkFinished(sdk)
    }

    init {
      data.putUserData(GradleJavaNewProjectWizardData.KEY, this)

      sdkProperty.afterChange { logSdkChanged(it) }
      useKotlinDslProperty.afterChange { logDslChanged(it) }
      parentProperty.afterChange { logParentChanged(!it.isPresent) }
      groupIdProperty.afterChange { logGroupIdChanged() }
      artifactIdProperty.afterChange { logArtifactIdChanged() }
      versionProperty.afterChange { logVersionChanged() }
    }
  }

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = "$path/$name"
      if (gitData?.git == true) {
        addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
      }
      if (addSampleCode) {
        withJavaSampleCodeAsset("src/main/java", groupId)
      }
    }
  }
}

fun <T> GradleNewProjectWizardStep<T>.generateModuleBuilder(): AbstractGradleModuleBuilder
  where T : NewProjectWizardStep, T : NewProjectWizardBaseData = InternalGradleModuleBuilder().apply {
  moduleJdk = sdk
  name = parentStep.name
  contentEntryPath = "${parentStep.path}/${parentStep.name}"

  isCreatingNewProject = context.isCreatingNewProject

  parentProject = parentData
  projectId = ProjectId(groupId, artifactId, version)
  isInheritGroupId = parentData?.group == groupId
  isInheritVersion = parentData?.version == version

  isUseKotlinDsl = useKotlinDsl
}