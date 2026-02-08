// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses

/**
 * Advertises VCS plugins that were unbundled, but the project uses them.
 */
internal class MissingVcsPluginAdvertiser : DependencyCollector {
  override suspend fun collectDependencies(project: Project): Collection<DependencyInformation> {
    return ProjectLevelVcsManager.getInstance(project)
      .getDirectoryMappings()
      .asSequence()
      .filter { AllVcses.getInstance(project).getByName(it.vcs) == null }
      .map {
        val vcsName = it.vcs
        DependencyInformation(vcsName.lowercase(),
                              VcsBundle.message("plugins.configurable.suggested.vcs.features", vcsName))
      }.toList()
  }
}
