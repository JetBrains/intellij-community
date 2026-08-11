// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperVersionQuickFixProvider
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path

abstract class ConfigurableGradleBuildIssue : ConfigurableBuildIssue() {

  /**
   * Adds a quick fix to update the Gradle wrapper version only if a Gradle wrapper properties file exists
   * according to [GradleUtil.findDefaultWrapperPropertiesFile],
   * and a [GradleWrapperVersionQuickFixProvider] is available.
   */
  fun addGradleWrapperVersionQuickFix(projectPath: String, gradleVersion: GradleVersion) {
    GradleUtil.findDefaultWrapperPropertiesFile(Path.of(projectPath)) ?: return
    val provider = GradleWrapperVersionQuickFixProvider.EP_NAME.extensionList.firstOrNull() ?: return
    val quickFix = provider.createQuickFix(projectPath, gradleVersion, requestImport = true)
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