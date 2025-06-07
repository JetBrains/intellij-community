// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.ConfigurableBuildIssue
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleAddDaemonToolchainCriteriaQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleDownloadToolchainQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleOpenDaemonJvmSettingsQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleRecreateToolchainDownloadUrlsQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.util.GradleBundle

abstract class ConfigurableGradleBuildIssue : ConfigurableBuildIssue() {

  fun addGradleVersionQuickFix(projectPath: String, gradleVersion: GradleVersion) {
    val quickFix = GradleVersionQuickFix(projectPath, gradleVersion, true)
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.version", hyperlinkReference, gradleVersion.version))
  }

  fun addGradleJvmQuickFix(projectPath: String, javaVersion: JavaVersion) {
    // Android Studio doesn't have Gradle JVM setting
    if ("AndroidStudio" == PlatformUtils.getPlatformPrefix()) return

    val quickFix = GradleSettingsQuickFix(
      projectPath, true,
      GradleSettingsQuickFix.GradleJvmChangeDetector,
      GradleBundle.message("gradle.settings.text.jvm.path")
    )
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.jvm", hyperlinkReference, javaVersion))
  }

  fun addDaemonToolchainCriteriaQuickFix(projectPath: String) {
    val quickFix = GradleAddDaemonToolchainCriteriaQuickFix(projectPath)
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.add.toolchain.criteria", hyperlinkReference))
  }

  fun addDownloadToolchainQuickFix(projectPath: String) {
    val quickFix = GradleDownloadToolchainQuickFix(projectPath)
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.install.missing.toolchain", hyperlinkReference))
  }

  fun addRecreateToolchainDownloadUrlsQuickFix(projectPath: String) {
    val quickFix = GradleRecreateToolchainDownloadUrlsQuickFix(projectPath)
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.recreate.download.urls", hyperlinkReference))
  }

  fun addOpenDaemonJvmSettingsQuickFix() {
    val quickFix = GradleOpenDaemonJvmSettingsQuickFix
    val hyperlinkReference = addQuickFix(quickFix)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.modify.gradle.jvm.criteria", hyperlinkReference))
  }
}