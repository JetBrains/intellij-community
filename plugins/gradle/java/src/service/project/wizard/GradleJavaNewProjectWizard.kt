// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logArtifactIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logDslChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logGroupIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logVersionChanged
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = GradleConstants.SYSTEM_ID.readableName

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent)

  class Step(parent: JavaNewProjectWizard.Step) :
    GradleNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    GradleJavaNewProjectWizardData {

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