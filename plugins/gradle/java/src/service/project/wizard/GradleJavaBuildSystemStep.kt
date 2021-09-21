// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleJavaBuildSystemStep(parent: JavaNewProjectWizard.Step)
  : GradleBuildSystemStep<JavaNewProjectWizard.Step>(parent) {

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

  class Factory : JavaBuildSystemType {
    override val name = GradleConstants.SYSTEM_ID.readableName

    override fun createStep(parent: JavaNewProjectWizard.Step) = GradleJavaBuildSystemStep(parent)
  }
}