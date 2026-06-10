// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

internal enum class SplitModeInspectionResourceReadMode(val registryValue: String) {
  BUNDLED_ONLY("bundled"),
  PROJECT_ONLY("project"),
  PREFER_PROJECT_WITH_BUNDLED_FALLBACK("project-or-bundled");

  companion object {
    private val LOG = logger<SplitModeInspectionResourceReadMode>()

    fun fromRegistryValue(
      registryKey: String,
      value: String,
      defaultMode: SplitModeInspectionResourceReadMode,
    ): SplitModeInspectionResourceReadMode {
      return entries.firstOrNull { it.registryValue == value } ?: defaultMode.also {
        LOG.warn("Unknown split-mode inspection resource read mode '$value' for $registryKey, using ${defaultMode.registryValue}")
      }
    }
  }
}

@Service(Service.Level.PROJECT)
internal class SplitModeInspectionResourceReader(private val project: Project) {

  companion object {
    private val LOG = logger<SplitModeInspectionResourceReader>()
    private const val DEVKIT_RESOURCES_PROJECT_RELATIVE_PATH = "community/plugins/devkit/devkit-core/resources"

    @JvmStatic
    fun getInstance(project: Project): SplitModeInspectionResourceReader = project.service()
  }

  fun readText(resourcePath: String, mode: SplitModeInspectionResourceReadMode): String? {
    val normalizedResourcePath = resourcePath.trimStart('/')
    return when (mode) {
      SplitModeInspectionResourceReadMode.BUNDLED_ONLY -> readBundledText(normalizedResourcePath)
      SplitModeInspectionResourceReadMode.PROJECT_ONLY -> readProjectText(normalizedResourcePath)
      SplitModeInspectionResourceReadMode.PREFER_PROJECT_WITH_BUNDLED_FALLBACK -> {
        readProjectText(normalizedResourcePath) ?: run {
          LOG.info("Falling back to bundled split-mode resource '$normalizedResourcePath'")
          readBundledText(normalizedResourcePath)
        }
      }
    }
  }

  private fun readProjectText(resourcePath: String): String? {
    val resourceFile = findProjectResourceFile(resourcePath)
    if (resourceFile == null) {
      LOG.info("Project split-mode resource '$resourcePath' does not exist")
      return null
    }

    return try {
      LOG.info("Loading split-mode resource '$resourcePath' from project file ${resourceFile.path}")
      VfsUtilCore.loadText(resourceFile)
    }
    catch (e: IOException) {
      LOG.warn("Cannot load split-mode resource '$resourcePath' from project file ${resourceFile.path}", e)
      return null
    }
  }

  private fun readBundledText(resourcePath: String): String? {
    val bundledPath = "/$resourcePath"
    val inputStream = SplitModeInspectionResourceReader::class.java.getResourceAsStream(bundledPath)
    if (inputStream == null) {
      LOG.warn("Bundled split-mode resource not found: $bundledPath")
      return null
    }

    LOG.info("Loading split-mode resource '$resourcePath' from bundled resource $bundledPath")
    return inputStream.bufferedReader().use { it.readText() }
  }

  internal fun findProjectResourceFile(resourcePath: String): VirtualFile? {
    return getProjectResourcesDirectory()?.findFileByRelativePath(resourcePath.trimStart('/'))
  }

  private fun getProjectResourcesDirectory(): VirtualFile? {
    return project.guessProjectDir()?.findFileByRelativePath(DEVKIT_RESOURCES_PROJECT_RELATIVE_PATH)
  }
}
