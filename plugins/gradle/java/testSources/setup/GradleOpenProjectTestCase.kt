// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.ide.actions.ImportProjectAction
import com.intellij.openapi.externalSystem.action.AttachExternalProjectAction
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.autolink.forEachExtensionSafeAsync
import com.intellij.openapi.externalSystem.util.performAction
import com.intellij.openapi.externalSystem.util.performOpenAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.utils.vfs.getDirectory
import com.intellij.testFramework.utils.vfs.getFile
import org.jetbrains.plugins.gradle.action.ImportProjectFromScriptAction
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.getSettingsScriptName
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.idea.maven.utils.MavenUtil as IntellijMavenUtil

abstract class GradleOpenProjectTestCase : GradleTestCase() {

  suspend fun importProject(projectInfo: ProjectInfo, numProjectSyncs: Int = 1): Project {
    return awaitOpenProjectConfiguration(numProjectSyncs) {
      performOpenAction(
        action = ImportProjectAction(),
        systemId = GradleConstants.SYSTEM_ID,
        selectedFile = testRoot.getDirectory(projectInfo.relativePath)
          .getFile(getSettingsScriptName(projectInfo.gradleDsl))
      )
    }
  }

  suspend fun attachProject(project: Project, relativePath: String) {
    awaitProjectConfiguration(project) {
      performAction(
        action = AttachExternalProjectAction(),
        project = project,
        systemId = GradleConstants.SYSTEM_ID,
        selectedFile = testRoot.getDirectory(relativePath)
      )
    }
  }

  suspend fun attachMavenProject(project: Project, relativePath: String) {
    val projectPath = testPath.resolve(relativePath).toCanonicalPath()
    EP_NAME.forEachExtensionSafeAsync { extension ->
      if (extension.systemId == IntellijMavenUtil.SYSTEM_ID) {
        extension.linkAndLoadProjectAsync(project, projectPath)
      }
    }
  }

  suspend fun attachProjectFromScript(project: Project, relativePath: String) {
    awaitProjectConfiguration(project) {
      performAction(
        action = ImportProjectFromScriptAction(),
        project = project,
        systemId = GradleConstants.SYSTEM_ID,
        selectedFile = testRoot.getDirectory(relativePath)
      )
    }
  }

  fun InspectionProfileImpl.ensureInitialized(project: Project) {
    if (!wasInitialized()) {
      val oldValue = InspectionProfileImpl.INIT_INSPECTIONS
      try {
        InspectionProfileImpl.INIT_INSPECTIONS = true
        initInspectionTools(project)
      }
      finally {
        InspectionProfileImpl.INIT_INSPECTIONS = oldValue
      }
    }
  }
}