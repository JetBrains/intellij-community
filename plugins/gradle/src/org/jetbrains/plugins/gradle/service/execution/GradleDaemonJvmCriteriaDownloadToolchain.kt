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
import org.jetbrains.plugins.gradle.util.toJvmVendor
import java.nio.file.Path
import kotlin.io.path.Path

object GradleDaemonJvmCriteriaDownloadToolchain {

  suspend fun downloadJdkMatchingCriteria(project: Project, externalProjectPath: String) {
    val jvmProperties = GradleDaemonJvmPropertiesFile.getProperties(Path(externalProjectPath))
    val version = jvmProperties?.version?.value
    val vendor = jvmProperties?.vendor?.value
    val (jdkItem, jdkHome) = pickJdkItemAndPathForMatchingCriteria(project, version, vendor) ?: return
    val downloadTask = JdkDownloadUtil.createDownloadTask(project, jdkItem, jdkHome) ?: return
    val sdk = JdkDownloadUtil.createDownloadSdk(ExternalSystemJdkUtil.getJavaSdkType(), downloadTask)
    JdkDownloadUtil.downloadSdk(sdk)
  }

  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun pickJdkItemAndPathForMatchingCriteria(project: Project, version: String?, vendor: String?): Pair<JdkItem, Path>? {
    val jdkItemAndPath = JdkDownloadUtil.pickJdkItemAndPath(project) { jdkItem ->
      matchesVersion(jdkItem, version) && matchesVendor(jdkItem, vendor)
    }
    if (jdkItemAndPath == null) {
      withContext(Dispatchers.EDT) {
        val title = GradleBundle.message("gradle.toolchain.download.error.title")
        val message = GradleBundle.message("gradle.toolchain.download.error.message",
                                           version ?: GradleBundle.message("gradle.toolchain.download.error.message.any"),
                                           vendor ?: GradleBundle.message("gradle.toolchain.download.error.message.any"))
        Messages.showErrorDialog(project, message, title)
      }
    }
    return jdkItemAndPath
  }

  private fun matchesVersion(jdkItem: JdkItem, version: String?): Boolean {
    return version == null || jdkItem.jdkMajorVersion.toString() == version
  }

  private fun matchesVendor(jdkItem: JdkItem, vendor: String?): Boolean {
    if (vendor == null) return true
    val adjustedVendor = vendor.toJvmVendor().displayName
    val jdkItemVendorName = jdkItem.product.vendor
    val jdkItemProductName = jdkItem.product.product?.split(" ")?.firstOrNull()
    return adjustedVendor.startsWith(jdkItemVendorName, ignoreCase = true) ||
           (jdkItemProductName != null && adjustedVendor.startsWith(jdkItemProductName, ignoreCase = true))
  }
}