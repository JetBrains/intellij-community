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
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitOpenProjectActivity
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitProjectActivity
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualFile
import org.jetbrains.plugins.gradle.action.ImportProjectFromScriptAction
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.jetbrains.plugins.gradle.testFramework.projectInfo.GradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.settingsScriptName
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.idea.maven.utils.MavenUtil as IntellijMavenUtil

abstract class GradleOpenProjectTestCase : GradleTestCase() {

  suspend fun importProject(projectInfo: GradleProjectInfo, numProjectSyncs: Int = 1): Project {
    return withAllowedProjectSyncs(numProjectSyncs) {
      awaitOpenProjectActivity {
        performOpenAction(
          action = ImportProjectAction(),
          systemId = GradleConstants.SYSTEM_ID,
          selectedFile = testPath.resolve(projectInfo.rootModule.relativePath)
            .resolve(projectInfo.rootModule.settingsScriptName)
            .refreshAndGetVirtualFile()
        )
      }
    }
  }

  suspend fun attachProject(project: Project, relativePath: String) {
    withAllowedProjectSyncs {
      awaitProjectActivity(project) {
        performAction(
          action = AttachExternalProjectAction(),
          project = project,
          systemId = GradleConstants.SYSTEM_ID,
          selectedFile = testPath.resolve(relativePath)
            .refreshAndGetVirtualDirectory()
        )
      }
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
    withAllowedProjectSyncs {
      awaitProjectActivity(project) {
        performAction(
          action = ImportProjectFromScriptAction(),
          project = project,
          systemId = GradleConstants.SYSTEM_ID,
          selectedFile = testPath.resolve(relativePath)
            .refreshAndGetVirtualDirectory()
        )
      }
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