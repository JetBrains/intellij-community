// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.ide.actions.ImportProjectAction
import com.intellij.openapi.externalSystem.action.AttachExternalProjectAction
import com.intellij.openapi.externalSystem.util.performAction
import com.intellij.openapi.externalSystem.util.performOpenAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.utils.vfs.getDirectory
import org.jetbrains.plugins.gradle.action.ImportProjectFromScriptAction
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.jetbrains.plugins.gradle.testFramework.util.ProjectInfo
import org.jetbrains.plugins.gradle.testFramework.util.getSettingsFile
import org.jetbrains.plugins.gradle.util.GradleConstants

abstract class GradleOpenProjectTestCase : GradleTestCase() {

  suspend fun importProject(projectInfo: ProjectInfo, wait: Boolean = true): Project {
    return closeOpenedProjectsIfFailAsync {
      awaitAnyGradleProjectReload(wait = wait) {
        performOpenAction(
          action = ImportProjectAction(),
          systemId = GradleConstants.SYSTEM_ID,
          selectedFile = testRoot.getSettingsFile(projectInfo.relativePath, projectInfo.useKotlinDsl)
        )
      }
    }
  }

  suspend fun attachProject(project: Project, relativePath: String) {
    awaitAnyGradleProjectReload {
      performAction(
        action = AttachExternalProjectAction(),
        project = project,
        systemId = GradleConstants.SYSTEM_ID,
        selectedFile = testRoot.getDirectory(relativePath)
      )
    }
  }

  suspend fun attachProjectFromScript(project: Project, relativePath: String) {
    awaitAnyGradleProjectReload {
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