// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.ConfigurableBuildIssue
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.util.GradleBundle

abstract class ConfigurableGradleBuildIssue : ConfigurableBuildIssue() {

  fun addGradleVersionQuickFix(projectPath: String, gradleVersion: GradleVersion) {
    val quickFix = GradleVersionQuickFix(projectPath, gradleVersion, true)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.version", quickFix.id, gradleVersion.version))
    addQuickFix(quickFix)
  }

  fun addGradleJvmQuickFix(projectPath: String, javaVersion: JavaVersion) {
    // Android Studio doesn't have Gradle JVM setting
    if ("AndroidStudio" == PlatformUtils.getPlatformPrefix()) return

    val gradleSettingsQuickFix = GradleSettingsQuickFix(
      projectPath, true,
      GradleSettingsQuickFix.GradleJvmChangeDetector,
      GradleBundle.message("gradle.settings.text.jvm.path")
    )
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.jvm", gradleSettingsQuickFix.id, javaVersion))
    addQuickFix(gradleSettingsQuickFix)
  }
}