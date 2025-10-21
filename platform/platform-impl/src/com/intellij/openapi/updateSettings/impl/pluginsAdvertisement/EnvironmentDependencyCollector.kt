// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

@ApiStatus.Internal
@IntellijInternalApi
internal class EnvironmentDependencyCollector : DependencyCollector {
  private val ALLOWED_EXECUTABLES: List<String> = listOf(
    "docker",
    "kubectl",
    "podman",
    "terraform",

    // Reserved for Cloud vendors only
    "az",
    "gcloud",
    "aws"
  )

  override suspend fun collectDependencies(project: Project): Collection<DependencyInformation> {
    val pathNames = EnvironmentScanner.getPathNames()

    return ALLOWED_EXECUTABLES
      .filter { EnvironmentScanner.hasToolInLocalPath(pathNames, it) }
      .map { DependencyInformation(it, IdeBundle.message("plugins.configurable.suggested.features.executable", it)) }
  }
}

@IntellijInternalApi
@ApiStatus.Internal
object EnvironmentScanner {
  fun getPathNames(): List<Path> {
    val fs = FileSystems.getDefault()
    val pathNames = EnvironmentUtil.getEnvironmentMap()["PATH"]?.split(fs.separator)
      ?.mapNotNull {
        try {
          fs.getPath(it)
        }
        catch (_: InvalidPathException) {
          null
        }
      }
      ?.filter(Files::exists)
    return pathNames ?: emptyList()
  }

  fun hasToolInLocalPath(pathNames: List<Path>, executableWithoutExt: String): Boolean {
    val baseNames = if (SystemInfo.isWindows) {
      sequenceOf(".bat", ".cmd", ".com", ".exe")
        .map { exeSuffix -> executableWithoutExt + exeSuffix }
    }
    else {
      sequenceOf(executableWithoutExt)
    }

    return pathNames.asSequence()
      .flatMap { pathEntry ->
        baseNames.map { basename -> pathEntry.resolve(basename) }
      }
      .filter(Path::isRegularFile)
      .filter(Path::isExecutable)
      .any()
  }
}
