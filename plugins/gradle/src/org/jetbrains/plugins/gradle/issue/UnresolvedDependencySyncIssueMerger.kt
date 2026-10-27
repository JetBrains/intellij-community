// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.util.GradleBundle

internal class UnresolvedDependencySyncIssueMerger : GradleIssueMerger {

  override fun isApplicable(issue: BuildIssue): Boolean = issue is UnresolvedDependencySyncIssue

  override fun merge(issues: List<BuildIssue>): List<BuildIssue> {
    val descriptionBuilder = StringBuilder()
    issues.filterIsInstance<UnresolvedDependencySyncIssue>()
      .groupBy { it.dependencyOwner }
      .forEach { (_, issues) ->
        descriptionBuilder
          .append("\n")
        for (issue in issues) {
          descriptionBuilder.append("    - ")
            .append(issue.dependencyName)
            .append("\n")
            .append(issue.description)
            .append("\n")
        }
      }
    return listOf(
      UnresolvedGroupedDependencyIssue(
        GradleBundle.message("gradle.build.issue.unresolved.dependencies.title"),
        descriptionBuilder.dropEmptyLines()
      )
    )
  }

  private fun CharSequence.dropEmptyLines(): String {
    val result = StringBuilder()
    var isPrevEmpty = false
    for (line in lines()) {
      if (line.isEmpty()) {
        isPrevEmpty = true
        continue
      }
      if (result.isNotEmpty()) {
        result.append('\n')
        if (isPrevEmpty) {
          result.append('\n')
        }
      }
      result.append(line)
      isPrevEmpty = false
    }
    return result.toString()
  }
}
