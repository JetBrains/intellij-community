// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.File

class K1GradleQuickFixTest : AbstractGradleMultiFileQuickFixTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun checkUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {
        DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile)
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJsAndJvm() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJsAndJvmTest() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvm() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTest() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTestWithCustomPath() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTestWithCustomExistentPath() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOS() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOSWithExistentPath() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOSWithExistentFile() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOSWithExistentDifferentPackage() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOSWithExistentEmptyFile() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForGranularSourceSetTarget() {
        doMultiFileQuickFixTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.0+")
    fun testAddKotlinTestLibraryJvm() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20+")
    fun testAddKotlinTestLibraryKmp() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20+")
    fun testAddKotlinTestLibraryKmpNativeMain() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" },
            afterDirectorySanitizer = { _, text ->
                val nativeMain = ModuleManager.getInstance(myProject).findModuleByName("project.nativeMain")
                    ?: error("Missing 'nativeMain' module")

                val version = KotlinLibraryVersionProvider.EP_NAME.extensionList
                    .firstNotNullOfOrNull { provider ->
                        provider.getVersion(
                            nativeMain,
                            "org.jetbrains.kotlinx",
                            "kotlinx-coroutines-core"
                        )
                    }
                    ?: error("Unknown compatible version for 'kotlinx-coroutines-core' library")

                val coroutinesCoordinatesBase = "org.jetbrains.kotlinx:kotlinx-coroutines-core:"
                text.replace("$coroutinesCoordinatesBase{{coroutines_version}}", coroutinesCoordinatesBase + version)
            }
        )
    }
}
