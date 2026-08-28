// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.atomic.AtomicReference

/** Resolves imported metadata for the closest Gradle module containing a physical PSI file. */
internal fun interface IntelliJPlatformGradleModelProvider {
  fun getModel(file: PsiFile): IntelliJPlatformGradleData?

  companion object {
    fun getInstance(project: Project): IntelliJPlatformGradleModelProvider =
      project.getService(IntelliJPlatformGradleModelProvider::class.java)
  }
}

/** Publishes per-sync model snapshots without blocking concurrent completion lookups. */
internal class IntelliJPlatformGradleModelProviderImpl : IntelliJPlatformGradleModelProvider {

  private val dataByLinkedProjectPath = AtomicReference<Map<String, Map<String, IntelliJPlatformGradleData>>>(emptyMap())

  override fun getModel(file: PsiFile): IntelliJPlatformGradleData? {
    val filePath = file.virtualFile?.path
    if (filePath == null) {
      LOG.debug("Cannot find IntelliJ Platform Gradle model for a non-physical file: ${file.name}")
      return null
    }

    val match = dataByLinkedProjectPath.get()
      .values
      .asSequence()
      .flatMap { dataByModulePath -> dataByModulePath.asSequence().map { it.toPair() } }
      .closestModel(filePath)
      ?: findCachedModel(file.project, filePath)

    if (LOG.isDebugEnabled) {
      LOG.debug("IntelliJ Platform Gradle model lookup for '$filePath': ${match?.first ?: "no matching Gradle module"}")
    }
    return match?.second
  }

  internal fun replaceProjectData(
    linkedProjectPath: String,
    dataByModulePath: Map<String, IntelliJPlatformGradleData>,
  ) {
    dataByLinkedProjectPath.updateAndGet { currentData ->
      if (dataByModulePath.isEmpty()) currentData - linkedProjectPath
      else currentData + (linkedProjectPath to dataByModulePath.toMap())
    }
  }

  companion object {
    private val LOG = Logger.getInstance(IntelliJPlatformGradleModelProviderImpl::class.java)
  }
}

private fun findCachedModel(project: Project, filePath: String): Pair<String, IntelliJPlatformGradleData>? {
  return ProjectDataManager.getInstance()
    .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
    .asSequence()
    .mapNotNull { it.externalProjectStructure }
    .flatMap { ExternalSystemApiUtil.findAllRecursively(it, IntelliJPlatformGradleData.KEY) }
    .mapNotNull { node ->
      val modulePath = (node.parent?.data as? ModuleData)?.linkedExternalProjectPath ?: return@mapNotNull null
      modulePath to node.data
    }
    .closestModel(filePath)
}

private fun Sequence<Pair<String, IntelliJPlatformGradleData>>.closestModel(
  filePath: String,
): Pair<String, IntelliJPlatformGradleData>? =
  filter { (modulePath) -> FileUtil.isAncestor(modulePath, filePath, false) }
    .maxByOrNull { (modulePath) -> modulePath.length }
