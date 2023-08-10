// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

internal class EnvironmentDependencyCollector : DependencyCollector {
  private val ALLOWED_EXECUTABLES: List<String> = listOf(
    "docker",
    "kubectl",
    "podman",
    "terraform"
  )

  override fun collectDependencies(project: Project): Collection<String> {
    val pathNames = EnvironmentScanner.getPathNames()

    return ALLOWED_EXECUTABLES
      .filter { EnvironmentScanner.hasToolInLocalPath(pathNames, it) }
  }
}

@ApiStatus.Internal
object EnvironmentScanner {
  fun getPathNames(): List<Path> {
    val fs = FileSystems.getDefault()
    val pathNames = System.getenv("PATH").split(File.pathSeparatorChar)
      .mapNotNull {
        try {
          fs.getPath(it)
        }
        catch (_: InvalidPathException) {
          null
        }
      }
      .filter(Files::exists)
    return pathNames
  }

  fun hasToolInLocalPath(pathNames: List<Path>, executableWithoutExt: String): Boolean {
    val baseNames = if (SystemInfo.isWindows) {
      sequenceOf(".bat", ".com", ".exe")
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
