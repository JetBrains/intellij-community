// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
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
      .flatMap { it.asSequence() }
      .filter { (modulePath) -> FileUtil.isAncestor(modulePath, filePath, false) }
      .maxByOrNull { (modulePath) -> modulePath.length }

    if (LOG.isDebugEnabled) {
      LOG.debug("IntelliJ Platform Gradle model lookup for '$filePath': ${match?.key ?: "no matching Gradle module"}")
    }
    return match?.value
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
