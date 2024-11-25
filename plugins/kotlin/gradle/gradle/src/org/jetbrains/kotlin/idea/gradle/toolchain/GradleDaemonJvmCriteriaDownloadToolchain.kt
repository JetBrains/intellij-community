// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.toolchain

import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.*
import com.intellij.openapi.ui.Messages
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.util.GradleBundle
import kotlin.io.path.Path

object GradleDaemonJvmCriteriaDownloadToolchain {

    fun downloadJdkMatchingCriteria(project: Project, externalProjectPath: String) {
        val jvmProperties = GradleDaemonJvmPropertiesFile.getProperties(Path(externalProjectPath))
        val jvmVersion = jvmProperties?.version?.value
        val jvmVendor = jvmProperties?.vendor?.value
        findDownloadableJdkMatchingCriteria(jvmVersion, jvmVendor)?.let { jdkItem ->
            val jdkInstallRequest = JdkInstallRequestInfo(jdkItem, JdkInstaller.getInstance().defaultInstallDir(jdkItem))
            val jdkDownloadTask = JdkDownloadTask(jdkItem, jdkInstallRequest, project)
            project.service<JdkDownloadService>().scheduleDownloadJdk(jdkDownloadTask)
        } ?: run {
            val errorMessage = jvmVendor?.let {
                GradleBundle.message("gradle.toolchain.download.error.message.with.vendor", jvmVersion, jvmVendor)
            } ?: run {
                GradleBundle.message("gradle.toolchain.download.error.message.without.vendor", jvmVersion)
            }
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, errorMessage, GradleBundle.message("gradle.toolchain.download.error.title"))
            }
        }
    }

    private fun findDownloadableJdkMatchingCriteria(jvmVersion: String?, jvmVendor: String?): JdkItem? {
        return JdkListDownloader.getInstance().downloadModelForJdkInstaller(null, JdkPredicate.default()).firstOrNull { jdkItem ->
            jvmVendor?.let {
                jdkItem.matchesVersion(jvmVersion) && jdkItem.matchesKnownVendor(jvmVendor)
            } ?: run {
                jdkItem.matchesVersion(jvmVersion)
            }
        }
    }

    private fun JdkItem.matchesVersion(version: String?) =
        jdkMajorVersion.toString() == version

    private fun JdkItem.matchesKnownVendor(vendor: String?) : Boolean {
        val vendorName = tryParseToKnownVendors(vendor) ?: return false // TODO replace parser with Gradle 8.13
        if (vendorName.startsWith(product.vendor, ignoreCase = true)) return true

        val itemProductName = product.product?.split(" ")?.firstOrNull() ?: return false
        return vendorName.startsWith(itemProductName, ignoreCase = true)
    }

    /**
     * Custom parser since Gradle JvmVendor.fromString(vendor) fails to parse "azul" or "zulu" being
     * this planned to be fixed in Gradle 8.13 allowing us to remove it in the future.
     */
    private fun tryParseToKnownVendors(rawVendor: String?): String? {
        if (rawVendor == null) return null
        val knownVendor = KnownJvmVendor.entries
            .firstOrNull { DefaultJvmVendorSpec.of(it).matches(rawVendor) }

        if (knownVendor == KnownJvmVendor.UNKNOWN || knownVendor == null) return rawVendor
        return knownVendor.asJvmVendor().displayName
    }
}