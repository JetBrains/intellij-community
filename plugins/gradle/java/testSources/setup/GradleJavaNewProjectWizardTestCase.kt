// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.withProjectAsync
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaNewProjectWizardData.Companion.javaGradleData
import org.jetbrains.plugins.gradle.testFramework.util.ModuleInfo
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Assertions

abstract class GradleJavaNewProjectWizardTestCase : GradleNewProjectWizardTestCase() {

  suspend fun createProjectByWizard(projectInfo: ProjectInfo): Project {
    Assertions.assertTrue(projectInfo.composites.isEmpty(), "NPW cannot create composite projects please use initProject instead.")
    val rootModuleInfo = projectInfo.rootModule
    return createProjectByWizard(JAVA) {
      configureWizardStepSettings(this, rootModuleInfo, parentData = null)
    }.withProjectAsync { project ->
      val parentPath = testPath.resolve(projectInfo.relativePath).toCanonicalPath()
      val parentData = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, parentPath)!!
      for (moduleInfo in projectInfo.modules) {
        if (moduleInfo != rootModuleInfo) {
          createModuleByWizard(project, JAVA) {
            configureWizardStepSettings(this, moduleInfo, parentData.data)
          }
        }
      }
    }
  }

  fun configureWizardStepSettings(step: NewProjectWizardStep, moduleInfo: ModuleInfo, parentData: ProjectData?) {
    step.baseData!!.name = moduleInfo.name
    step.baseData!!.path = testPath.resolve(moduleInfo.relativePath).normalize().parent.toCanonicalPath()
    step.javaBuildSystemData!!.buildSystem = GRADLE
    step.javaGradleData!!.gradleDsl = moduleInfo.gradleDsl
    step.javaGradleData!!.parentData = parentData
    step.javaGradleData!!.groupId = moduleInfo.groupId
    step.javaGradleData!!.artifactId = moduleInfo.artifactId
    step.javaGradleData!!.version = moduleInfo.version
    step.javaGradleData!!.addSampleCode = false
  }

  fun ModuleInfo.Builder.withJavaBuildFile() {
    withBuildFile {
      addGroup(groupId)
      addVersion(version)
      withJavaPlugin()
      withJUnit()
    }
  }
}