// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.util.GradleBundle

internal class UnresolvedDependencySyncIssueMerger : GradleIssueMerger {

  override fun isApplicable(issue: BuildIssue): Boolean = issue is UnresolvedDependencySyncIssue

  override fun merge(issues: List<BuildIssue>): List<BuildIssue> {
    var offlineMode = false
    val descriptionBuilder = StringBuilder(GradleBundle.message("gradle.build.issue.unresolved.dependencies.description"))
      .append("\n")
    issues.filterIsInstance<UnresolvedDependencySyncIssue>()
      .groupBy { it.dependencyOwner }
      .forEach { (configuration, issues) ->
        descriptionBuilder
          .append("\n")
          .append(GradleBundle.message("gradle.build.issue.unresolved.dependencies.configuration", configuration))
          .append("\n")
        for (issue in issues) {
          descriptionBuilder.append("    - ").append(issue.dependencyName).append("\n")
          offlineMode = issue.isOfflineMode
        }
      }
    return listOf(
      UnresolvedGroupedDependencyIssue(
        GradleBundle.message("gradle.build.issue.unresolved.dependencies.title"),
        descriptionBuilder.toString(),
        offlineMode)
    )
  }
}
