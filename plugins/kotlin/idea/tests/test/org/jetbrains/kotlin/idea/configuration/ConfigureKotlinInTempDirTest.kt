// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JsCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.project.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(JUnit38ClassRunner::class)
open class ConfigureKotlinInTempDirTest : AbstractConfigureKotlinInTempDirTest() {
    private fun checkKotlincPresence(present: Boolean = true) {
        TestCase.assertEquals(present, File(project.basePath, ".idea/kotlinc.xml").exists())
    }

    private fun moduleFileContent() = String(module.moduleFile!!.contentsToByteArray(), StandardCharsets.UTF_8)

    fun testNoKotlincExistsNoSettingsRuntime10() {
        val application = ApplicationManager.getApplication() as ApplicationImpl
        application.isSaveAllowed = true
        Assert.assertEquals(LanguageVersion.KOTLIN_1_0, module.languageVersionSettings.languageVersion)
        Assert.assertEquals(LanguageVersion.KOTLIN_1_0, myProject.getLanguageVersionSettings(null).languageVersion)
        application.saveAll()
        checkKotlincPresence(false)
    }

    fun testTwoModulesWithNonDefaultPath_doNotCopyInDefault() {
        doTestConfigureModulesWithNonDefaultSetup(jvmConfigurator)
        assertEmpty(getCanBeConfiguredModules(myProject, jsConfigurator))
    }

    fun testTwoModulesWithJSNonDefaultPath_doNotCopyInDefault() {
        doTestConfigureModulesWithNonDefaultSetup(jsConfigurator)
        assertEmpty(getCanBeConfiguredModules(myProject, jvmConfigurator))
    }

    fun testSimple() {
        assertNotConfigured(module, jvmConfigurator)
        jvmConfigurator.configure(myProject, emptyList())
        assertProperlyConfigured(module, jvmConfigurator)
    }

    fun testNoKotlincExistsNoSettingsLatestRuntime() {
        val application = ApplicationManager.getApplication() as ApplicationImpl
        application.isSaveAllowed = true
        Assert.assertEquals(LanguageVersion.LATEST_STABLE, module.languageVersionSettings.languageVersion)
        Assert.assertEquals(LanguageVersion.LATEST_STABLE, myProject.getLanguageVersionSettings(null).languageVersion)
        application.saveAll()
        checkKotlincPresence(false)
    }

    fun testKotlincExistsNoSettingsLatestRuntimeNoVersionAutoAdvance() {
        val application = ApplicationManager.getApplication() as ApplicationImpl
        application.isSaveAllowed = true
        Assert.assertEquals(LanguageVersion.LATEST_STABLE, module.languageVersionSettings.languageVersion)
        Assert.assertEquals(LanguageVersion.LATEST_STABLE, myProject.getLanguageVersionSettings(null).languageVersion)
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            autoAdvanceLanguageVersion = false
            autoAdvanceApiVersion = false
        }
        application.saveAll()
        checkKotlincPresence()
    }

    fun testDropKotlincOnVersionAutoAdvance() {
        val application = ApplicationManager.getApplication() as ApplicationImpl
        application.isSaveAllowed = true
        Assert.assertEquals(LanguageVersion.KOTLIN_1_4, module.languageVersionSettings.languageVersion)
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            autoAdvanceLanguageVersion = true
            autoAdvanceApiVersion = true
        }
        application.saveAll()
        checkKotlincPresence(false)
    }

    fun testProject107InconsistentVersionInConfig() {
        val settings = KotlinFacetSettingsProvider.getInstance(myProject)?.getInitializedSettings(module)
            ?: error("Facet settings are not found")

        Assert.assertEquals(false, settings.useProjectSettings)
        Assert.assertEquals(LanguageVersion.KOTLIN_1_0, settings.languageLevel!!)
        Assert.assertEquals(LanguageVersion.KOTLIN_1_0, settings.apiLevel!!)
    }

    fun testFacetWithProjectSettings() {
        val settings = KotlinFacetSettingsProvider.getInstance(myProject)?.getInitializedSettings(module)
            ?: error("Facet settings are not found")

        Assert.assertEquals(true, settings.useProjectSettings)
        Assert.assertEquals(LanguageVersion.KOTLIN_1_1, settings.languageLevel!!)
        Assert.assertEquals(LanguageVersion.KOTLIN_1_1, settings.apiLevel!!)
        Assert.assertEquals(
            "-version -Xallow-kotlin-package -Xskip-metadata-version-check",
            settings.compilerSettings!!.additionalArguments
        )
    }

    fun testLoadAndSaveProjectWithV2FacetConfig() {
        val moduleFileContentBefore = moduleFileContent()
        val application = ApplicationManager.getApplication() as ApplicationImpl
        application.isSaveAllowed = true
        application.saveAll()
        val moduleFileContentAfter = moduleFileContent()
        Assert.assertEquals(moduleFileContentBefore, moduleFileContentAfter)
    }

    fun testLoadAndSaveOldNativePlatformOldNativeFacet() = doTestLoadAndSaveProjectWithFacetConfig(
        "platform=\"Native \"",
        "platform=\"Native (general) \" allPlatforms=\"Native []/Native [general]\""
    )

    fun testLoadAndSaveOldNativePlatformNewNativeFacet() = doTestLoadAndSaveProjectWithFacetConfig(
        "platform=\"Native \" allPlatforms=\"Native []\"",
        "platform=\"Native (general) \" allPlatforms=\"Native []/Native [general]\""
    )

    //TODO(auskov): test parsing common target platform with multiple versions of java, add parsing common platforms
    fun testLoadAndSaveProjectWithV2OldPlatformFacetConfig() = doTestLoadAndSaveProjectWithFacetConfig(
        "platform=\"JVM 1.8\"",
        "platform=\"JVM 1.8\" allPlatforms=\"JVM [1.8]\""
    )

    fun testLoadAndSaveProjectHMPPFacetConfig() = doTestLoadAndSaveProjectWithFacetConfig(
        "platform=\"Common (experimental) \" allPlatforms=\"JS []/JVM [1.6]/Native []\"",
        "platform=\"Common (experimental) \" allPlatforms=\"JS []/JVM [1.6]/Native []/Native [general]\""
    )

    fun testApiVersionWithoutLanguageVersion() {
        KotlinCommonCompilerArgumentsHolder.getInstance(myProject)
        val settings = myProject.getLanguageVersionSettings()
        Assert.assertEquals(ApiVersion.KOTLIN_1_1, settings.apiVersion)
    }

    fun testNoKotlincExistsNoSettingsLatestRuntimeNullizeEmptyStrings() {
        val application = ApplicationManager.getApplication() as ApplicationImpl
        application.isSaveAllowed = true
        Kotlin2JsCompilerArgumentsHolder.getInstance(project).update {
            sourceMapPrefix = ""
            sourceMapEmbedSources = ""
        }
        application.saveAll()
        checkKotlincPresence(false)
    }

    private fun doTestLoadAndSaveProjectWithFacetConfig(valueBefore: String, valueAfter: String) {
        val facetManager = FacetManager.getInstance(module)
        val moduleFileContentBefore = moduleFileContent()
        Assert.assertTrue(moduleFileContentBefore.contains(valueBefore))
        val application = ApplicationManager.getApplication() as ApplicationImpl
        facetManager.allFacets.forEach { facetManager.facetConfigurationChanged(it) }
        application.isSaveAllowed = true
        application.saveAll()
        val moduleFileContentAfter = moduleFileContent()
        Assert.assertEquals(moduleFileContentBefore.replace(valueBefore, valueAfter), moduleFileContentAfter)
    }

}
