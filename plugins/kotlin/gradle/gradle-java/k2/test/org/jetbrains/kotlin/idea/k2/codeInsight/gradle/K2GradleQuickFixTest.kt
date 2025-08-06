// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.openapi.module.ModuleManager
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleMultiFileQuickFixTest
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.idea.fir.K2DirectiveBasedActionUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Ignore
import org.junit.Test
import java.io.File

class K2GradleQuickFixTest : AbstractGradleMultiFileQuickFixTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun checkUnexpectedErrors(mainFile: File, ktFile: KtFile, fileText: String) {
        K2DirectiveBasedActionUtils.checkForUnexpectedErrors(mainFile, ktFile, fileText)
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

    @Test
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testEnableMultiDollarInterpolationJvm() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testDoNotSuggestEnableFeaturesWithoutXFlagJvm() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testEnableMultiDollarInterpolationJvmTest() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0 <=> 2.1.255")
    fun testEnableMultiDollarInterpolationKmp() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.22")
    fun testDoNotEnableMultiDollarInterpolationLowKgpVersion() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @Ignore("KTIJ-32414")
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testEnableMultiDollarInterpolationJvmWithOtherFlagsAdd() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testEnableMultiDollarInterpolationJvmWithOtherFlagsAddAll() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testEnableUpdatedAnnotationDefaultingRule() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1+")
    fun testEnableUpdatedAnnotationDefaultingRuleField() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0 <=> 2.1.255")
    fun testEnableContextParametersKotlin22() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0 <=> 2.1.255")
    fun testEnableContextParametersNoFixKotlin21() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.0 <=> 2.1.255")
    fun testEnableContextParametersNoFixKotlin22WithAFlag() {
        doMultiFileQuickFixTest(
            ignoreChangesInBuildScriptFiles = false,
            additionalResultFileFilter = { file -> file.name != "settings.gradle.kts" }
        )
    }
}
