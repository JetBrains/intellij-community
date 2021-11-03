// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = GradleConstants.SYSTEM_ID.readableName

  override fun createStep(parent: JavaNewProjectWizard.Step) =
    object : GradleNewProjectWizardStep<JavaNewProjectWizard.Step>(parent) {
      override fun setupProject(project: Project) {
        val builder = InternalGradleModuleBuilder().apply {
          moduleJdk = sdk
          name = parentStep.name
          contentEntryPath = parentStep.projectPath.systemIndependentPath

          isCreatingNewProject = context.isCreatingNewProject

          parentProject = parentData
          projectId = ProjectId(groupId, artifactId, version)
          isInheritGroupId = parentData?.group == groupId
          isInheritVersion = parentData?.version == version

          isUseKotlinDsl = useKotlinDsl

          gradleVersion = suggestGradleVersion()
        }

        builder.configureBuildScript {
          it.withJavaPlugin()
          it.withJUnit()
        }

        ExternalProjectsManagerImpl.setupCreatedProject(project)
        builder.commit(project)
      }
    }
}