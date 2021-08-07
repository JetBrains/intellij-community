// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.starters

import com.intellij.ide.starters.StarterModuleImporter
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.gradle.service.project.open.canLinkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

internal class GradleStarterModuleImporter : StarterModuleImporter {
  override val id: String = "gradle"
  override val title: String = "Gradle"

  override fun runAfterSetup(module: Module): Boolean {
    val project = module.project
    val gradleFile = findGradleFile(module) ?: return true

    val rootDirectory = gradleFile.parent
    fixGradlewExecutableFlag(gradleFile.parentFile)

    if (!canLinkAndRefreshGradleProject(rootDirectory, project)) return false
    linkAndRefreshGradleProject(rootDirectory, project)
    return false
  }

  private fun findGradleFile(module: Module): File? {
    for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
      val baseDir = VfsUtilCore.virtualToIoFile(contentRoot)
      var file = File(baseDir, GradleConstants.DEFAULT_SCRIPT_NAME)
      if (file.exists()) return file
      file = File(baseDir, GradleConstants.KOTLIN_DSL_SCRIPT_NAME)
      if (file.exists()) return file
    }
    return null
  }

  private fun fixGradlewExecutableFlag(containingDir: File) {
    val toFix = File(containingDir, "gradlew")
    if (toFix.exists()) {
      toFix.setExecutable(true, false)
    }
  }
}