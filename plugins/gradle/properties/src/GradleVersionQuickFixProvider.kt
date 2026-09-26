// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties

import com.intellij.build.issue.BuildIssueQuickFix
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperVersionQuickFixProvider

internal class GradleVersionQuickFixProvider : GradleWrapperVersionQuickFixProvider {

  override fun createQuickFix(projectPath: String, gradleVersion: GradleVersion, requestImport: Boolean): BuildIssueQuickFix {
    return GradleVersionQuickFix(projectPath, gradleVersion, requestImport)
  }
}
