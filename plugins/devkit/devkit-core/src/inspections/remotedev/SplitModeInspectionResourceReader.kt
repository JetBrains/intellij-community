// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.remotedev

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

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
    val path = resolveProjectResourcePath(resourcePath)
    if (path == null) {
      LOG.info("Cannot load split-mode resource '$resourcePath' from project: project base path is unknown")
      return null
    }
    if (!Files.isRegularFile(path)) {
      LOG.info("Project split-mode resource '$resourcePath' does not exist: $path")
      return null
    }

    LOG.info("Loading split-mode resource '$resourcePath' from project file $path")
    return path.readText()
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

  private fun resolveProjectResourcePath(resourcePath: String): Path? {
    val basePath = project.basePath ?: return null
    return Path.of(basePath).resolve(DEVKIT_RESOURCES_PROJECT_RELATIVE_PATH).resolve(resourcePath)
  }
}
