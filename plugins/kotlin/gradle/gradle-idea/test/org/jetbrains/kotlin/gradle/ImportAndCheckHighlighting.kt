// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.gradle

import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.checkers.utils.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.util.parseKotlinVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.PrintStream

class ImportAndCheckHighlighting : MultiplePluginVersionGradleImportingTestCase() {
    @Test
    @PluginTargetVersions(pluginVersion = "1.3.40+")
    fun testMultiplatformLibrary() {
        importAndCheckHighlighting(checkLineMarkers = true)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.3.40+")
    fun testUnresolvedInMultiplatformLibrary() {
        importAndCheckHighlighting(checkWarnings = true)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.4.0+")
    fun testConsumingKotlinXDatetimeInNativeMain() {
        importAndCheckHighlighting()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.3.40+", gradleVersionForLatestPlugin = mppImportTestMinVersionForMaster)
    fun testMacosTargets() {
        importAndCheckHighlighting(checkLineMarkers = true)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.4.0+")
    fun testHmppStdlibUsageInAllBackends() {
        importAndCheckHighlighting()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.20+")
    fun testCommonizeDummyCInterop() {
        importAndCheckHighlighting()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.3.30+")
    fun testKTIJ10023LibraryDependenciesCacheIOException() {
        configureByFiles()
        importProject()
        checkHighligthingOnAllModules()
    }

    private fun importAndCheckHighlighting(testLineMarkers: Boolean = true, checkWarnings: Boolean = true) {
        val files = configureByFiles()
        importProject()
        val project = myTestFixture.project
        checkFiles(
            files.filter { it.extension == "kt" || it.extension == "java" },
            project,
            object : GradleDaemonAnalyzerTestCase(
                testLineMarkers = testLineMarkers,
                checkWarnings = checkWarnings,
                checkInfos = false,
                rootDisposable = testRootDisposable
            ) {
                init {
                    allowTreeAccessForAllFiles()
                }
            }
        )
    }

    override fun printOutput(stream: PrintStream, text: String) = stream.println(text)
}
