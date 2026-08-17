// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.EnvironmentScanner

internal class EnvironmentDependencyCollector : DependencyCollector {
  private val ALLOWED_EXECUTABLES: List<String> = listOf(
    "docker",
    "kubectl",
    "podman",
    "terraform",

    // AI coding CLIs
    "copilot",
    "codex",
    "claude",

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
