// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.toolchain

import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.impl.jdkDownloader.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
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
        val jdkItems = createJdkItemsSample()
        val jdkListDownloader = mock<JdkListDownloader>().also {
            whenever(it.downloadModelForJdkInstaller(null, JdkPredicate.default())).thenReturn(jdkItems)
        }
        ApplicationManager.getApplication().replaceService(JdkListDownloader::class.java, jdkListDownloader, project)

        jdkItems.forEachIndexed { index, vendor ->
            createDaemonJvmPropertiesFile(21, vendor.product.vendor)
            GradleDaemonJvmCriteriaDownloadToolchain.downloadJdkMatchingCriteria(project, project.basePath.orEmpty())

            assertJdkDownloader(21, vendor.product.vendor, index + 1)
        }
    }

    fun `test Given different format vendors When download matching JDK Then requested item contains expected values`() {
        val jdkItemJetbrains = simpleJdkItem("JetBrains", "Runtime", 17)
        val jdkListDownloader = mock<JdkListDownloader>().also {
            whenever(it.downloadModelForJdkInstaller(null, JdkPredicate.default())).thenReturn(listOf(jdkItemJetbrains))
        }
        ApplicationManager.getApplication().replaceService(JdkListDownloader::class.java, jdkListDownloader, project)

        listOf("JetBrains", "jetbrains", "JETBRAINS", " JetBrains ").forEachIndexed { index, vendor ->
            createDaemonJvmPropertiesFile(17, vendor)
            GradleDaemonJvmCriteriaDownloadToolchain.downloadJdkMatchingCriteria(project, project.basePath.orEmpty())

            assertJdkDownloader(17, "JetBrains", index + 1)
        }
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

    private fun createJdkItemsSample() = listOf(
        simpleJdkItem("Oracle", "OpenJDK", 21),
        simpleJdkItem("Amazon", "Corretto", 21),
        simpleJdkItem("BellSoft", "Liberica JDK", 21),
        simpleJdkItem("Azul", "Zulu Community™", 21),
        simpleJdkItem("SAP", "SapMachine", 21),
        simpleJdkItem("Eclipse", "Temurin", 21),
        simpleJdkItem("IBM", "Semeru", 21),
        simpleJdkItem("GraalVM", "Community Edition", 21),
        simpleJdkItem("JetBrains", "Runtime", 21)
    )

    private fun simpleJdkItem(vendor: String, product: String, version: Int) = mock<JdkItem>().also {
        whenever(it.product).thenReturn(JdkProduct(vendor = vendor, product = product, flavour = null))
        whenever(it.jdkMajorVersion).thenReturn(version)
        whenever(it.installFolderName).thenReturn("installFolderName")
    }
}