// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.toJvmCriteria
import java.nio.file.Path

object GradleDaemonJvmCriteriaDownloadToolchain {

  suspend fun downloadJdkMatchingCriteria(project: Project, externalProjectPath: String) {
    val daemonJvmProperties = GradleDaemonJvmPropertiesFile.getProperties(Path.of(externalProjectPath))
    val (jdkItem, jdkHome) = pickJdkItemAndPathForMatchingCriteria(project, daemonJvmProperties.criteria) ?: return
    val downloadTask = JdkDownloadUtil.createDownloadTask(project, jdkItem, jdkHome) ?: return
    val sdk = JdkDownloadUtil.createDownloadSdk(ExternalSystemJdkUtil.getJavaSdkType(), downloadTask)
    JdkDownloadUtil.downloadSdk(sdk)
  }

  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun pickJdkItemAndPathForMatchingCriteria(project: Project, daemonJvmCriteria: GradleDaemonJvmCriteria): Pair<JdkItem, Path>? {
    val jdkItemAndPath = JdkDownloadUtil.pickJdkItemAndPath(project) { jdkItem ->
      jdkItem.toJvmCriteria().matches(daemonJvmCriteria)
    }
    if (jdkItemAndPath == null) {
      withContext(Dispatchers.EDT) {
        val title = GradleBundle.message("gradle.toolchain.download.error.title")
        val version = daemonJvmCriteria.version ?: GradleBundle.message("gradle.toolchain.download.error.message.any")
        val vendor = daemonJvmCriteria.vendor?.displayName ?: GradleBundle.message("gradle.toolchain.download.error.message.any")
        val message = GradleBundle.message("gradle.toolchain.download.error.message", version, vendor)
        Messages.showErrorDialog(project, message, title)
      }
    }
    return jdkItemAndPath
  }
}