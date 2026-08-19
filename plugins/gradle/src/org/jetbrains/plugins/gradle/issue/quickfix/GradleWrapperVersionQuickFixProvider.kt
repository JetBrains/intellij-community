// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus

/**
 * Creates a quick fix that updates the Gradle version in the Gradle wrapper properties file.
 */
@ApiStatus.Internal
interface GradleWrapperVersionQuickFixProvider {

  /**
   * @param projectPath path of the linked Gradle project whose wrapper should be updated.
   * @param gradleVersion the Gradle version to write into the wrapper properties file.
   * @param requestImport whether the project should be re-synced once the wrapper has been updated.
   */
  fun createQuickFix(projectPath: String, gradleVersion: GradleVersion, requestImport: Boolean): BuildIssueQuickFix

  companion object {
    val EP_NAME: ExtensionPointName<GradleWrapperVersionQuickFixProvider> =
      ExtensionPointName("org.jetbrains.plugins.gradle.wrapperVersionQuickFixProvider")
  }
}
