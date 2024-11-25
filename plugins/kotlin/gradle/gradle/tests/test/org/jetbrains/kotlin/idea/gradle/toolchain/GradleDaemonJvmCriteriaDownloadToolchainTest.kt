// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.toolchain

import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import java.nio.file.Files
import kotlin.io.path.Path

class GradleDaemonJvmCriteriaDownloadToolchainTest : BasePlatformTestCase() {

    private lateinit var jdkDownloadService: JdkDownloadService

    override fun setUp() {
        super.setUp()
        jdkDownloadService = mock(JdkDownloadService::class.java)
        project.replaceService(JdkDownloadService::class.java, jdkDownloadService, testRootDisposable)
    }

    fun `test Given unsupported criteria When download matching JDK Then expected exception is thrown`() {
        lateinit var exceptionMessage: String
        TestDialogManager.setTestDialog({
            exceptionMessage = it
            Messages.OK
        }, testRootDisposable)

        createDaemonJvmPropertiesFile(0, "unknown vendor")
        GradleDaemonJvmCriteriaDownloadToolchain.downloadJdkMatchingCriteria(project, project.basePath.orEmpty())

        UIUtil.dispatchAllInvocationEvents()
        assertTrue(exceptionMessage.startsWith("Failed to locate and download a matching toolchain"))
    }

    fun `test Given supported vendors When download matching JDK Then requested item contains expected values`() {
        lateinit var lastError: Throwable
        repeat(5) {
            val result = runCatching {
                listOf("Oracle", "Amazon", "BellSoft", "Azul", "SAP", "Eclipse", "IBM", "GraalVM", "JetBrains").forEachIndexed { index, vendor ->
                    createDaemonJvmPropertiesFile(21, vendor)
                    GradleDaemonJvmCriteriaDownloadToolchain.downloadJdkMatchingCriteria(project, project.basePath.orEmpty())

                    assertJdkDownloader(21, vendor, index + 1)
                }
            }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()!!

            if (lastError.message?.startsWith("Failed to locate and download a matching toolchain") == true) {
                Thread.sleep(5000)
            } else throw lastError
        }
        throw RuntimeException("Failed to locate and download a matching toolchain within several tries", lastError)
    }

    fun `test Given different format vendors When download matching JDK Then requested item contains expected values`() {
        lateinit var lastError: Throwable
        repeat(5) {
            val result = runCatching {
                listOf("JetBrains", "jetbrains", "JETBRAINS", " JetBrains ").forEachIndexed { index, vendor ->
                    createDaemonJvmPropertiesFile(17, vendor)
                    GradleDaemonJvmCriteriaDownloadToolchain.downloadJdkMatchingCriteria(project, project.basePath.orEmpty())

                    assertJdkDownloader(17, "JetBrains", index + 1)
                }
            }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()!!

            if (lastError.message?.startsWith("Failed to locate and download a matching toolchain") == true) {
                Thread.sleep(5000)
            } else throw lastError
        }
        throw RuntimeException("Failed to locate and download a matching toolchain within several tries", lastError)
    }

    private fun createDaemonJvmPropertiesFile(version: Int, vendor: String) {
        val projectRoot = Path(project.basePath.orEmpty())
        Files.createDirectories(projectRoot.resolve("gradle"))
        Files.writeString(
            projectRoot.resolve("gradle/gradle-daemon-jvm.properties"), """
            toolchainVersion=$version
            toolchainVendor=$vendor
        """.trimIndent()
        )
    }

    private fun assertJdkDownloader(expectedVersion: Int, expectedVendor: String, expectedInvocations: Int) {
        val jdkDownloadTaskCaptor = argumentCaptor<JdkDownloadTask>()
        verify(jdkDownloadService, times(expectedInvocations)).scheduleDownloadJdk(jdkDownloadTaskCaptor.capture())

        val downloadableJdkItem = jdkDownloadTaskCaptor.lastValue.jdkItem
        assertEquals(expectedVersion, downloadableJdkItem.jdkMajorVersion)
        assertEquals(expectedVendor, downloadableJdkItem.product.vendor)
    }
}