// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue

class OutdatedIntelliJPlatformGradlePluginVersionIssue(
  projectPath: String,
  private val currentVersion: GradleVersion,
  private val latestVersion: GradleVersion,
) : ConfigurableGradleBuildIssue() {

  init {
    setTitle(DevKitGradleBundle.message("intellij.platform.gradle.plugin.outdated.version.title"))
    addDescription(
      DevKitGradleBundle.message(
        "intellij.platform.gradle.plugin.outdated.version.description",
        currentVersion.version
      )
    )

    // TODO: Add a quick fix to update the plugin version, see [OutdatedGradleVersionIssue]
  }
}
