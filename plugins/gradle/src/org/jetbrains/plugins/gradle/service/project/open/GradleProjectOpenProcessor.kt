// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.open

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import icons.GradleIcons
import org.jetbrains.plugins.gradle.util.GradleBundle
import javax.swing.Icon

class GradleProjectOpenProcessor : ProjectOpenProcessor() {
  override val name: String
    get() = GradleBundle.message("gradle.name")

  override val icon: Icon
    get() = GradleIcons.Gradle

  override fun canOpenProject(file: VirtualFile): Boolean = canOpenGradleProject(file)

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return runUnderModalProgressIfIsEdt { openGradleProject(virtualFile, projectToClose, forceOpenInNewFrame) }
  }

  override suspend fun openProjectAsync(virtualFile: VirtualFile,
                                        projectToClose: Project?,
                                        forceOpenInNewFrame: Boolean): Project? {
    return openGradleProject(projectFile = virtualFile, projectToClose = projectToClose, forceOpenInNewFrame = forceOpenInNewFrame)
  }

  override fun canImportProjectAfterwards(): Boolean = true

  override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) {
    linkAndRefreshGradleProject(file.path, project)
  }
}
