// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.findLibrary
import org.jetbrains.kotlin.idea.base.util.invalidateProjectRoots
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JsCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.macros.KotlinBundledUsageDetector
import org.jetbrains.kotlin.idea.macros.KotlinBundledUsageDetectorListener
import org.jetbrains.kotlin.idea.notification.catchNotificationText
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(JUnit38ClassRunner::class)
class ConfigureKotlinInTempDirTest : AbstractConfigureKotlinInTempDirTest() {
    private fun checkKotlincPresence(present: Boolean = true, jpsVersionOnly: Boolean = false) {
        val file = File(project.basePath, ".idea/kotlinc.xml")
        assertEquals(present, file.exists())
        if (!present) return

        val children = JDOMUtil.load(file).children
        assertNotEmpty(children)

        val jpsSettingsElement = children.singleOrNull {
            it.getAttributeValue("name") == KotlinJpsPluginSettings::class.java.simpleName
        }

        val childrenNames = children.joinToString(prefix = "[", postfix = "]") { it.getAttributeValue("name") }
        if (jpsVersionOnly) {
            assertNotNull(jpsSettingsElement)
            assertTrue(
                /* message = */ "kotlinc.xml contains not only jps settings: $childrenNames",
                /* condition = */ children.size == 1,
            )
        } else {
            assertTrue(
                /* message = */ "non-jps settings is not found: $childrenNames",
                /* condition = */ jpsSettingsElement == null || children.size > 1,
            )
        }
    }

    private fun moduleFileContent() = String(module.moduleFile!!.contentsToByteArray(), StandardCharsets.UTF_8)

    fun testNoKotlincExistsNoSettingsRuntime10() {
        Assert.assertEquals(LanguageVersion.KOTLIN_1_0, module.languageVersionSettings.languageVersion)
        Assert.assertEquals(LanguageVersion.KOTLIN_1_0, myProject.languageVersionSettings.languageVersion)
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
        checkKotlincPresence(false) // TODO: replace to "jpsVersionOnly = true" after KTI-724
    }

    fun testKotlinBundledAdded() {
        assertFalse(KotlinBundledUsageDetector.isKotlinBundledPotentiallyUsedInLibraries(project))

        val connection = project.messageBus.connect(testRootDisposable)

        val kotlinBundledDetected = CompletableFuture<Unit>()
        connection.subscribe(KotlinBundledUsageDetector.TOPIC, object : KotlinBundledUsageDetectorListener {
            override fun kotlinBundledDetected() {
                kotlinBundledDetected.complete(Unit)
            }
        })

        runWriteAction {
            val kotlinRuntimeLibrary = module.findLibrary { it.name == "BundledKotlinStdlib" }
            assertNotNull(kotlinRuntimeLibrary)

            with(kotlinRuntimeLibrary!!.modifiableModel) {
                addRoot(KotlinArtifacts.kotlinStdlib.absolutePath, OrderRootType.CLASSES)
                commit()
            }
        }

        connection.deliverImmediately()

        kotlinBundledDetected.get(5, TimeUnit.SECONDS)
        assertTrue(KotlinBundledUsageDetector.isKotlinBundledPotentiallyUsedInLibraries(project))
    }

    fun testMigrationNotificationWithStdlib() {
        val notificationText = catchNotificationText(project) {
            val languageVersionSettingsBefore = module.languageVersionSettings
            Assert.assertEquals(LanguageVersion.KOTLIN_1_5, languageVersionSettingsBefore.languageVersion)
            Assert.assertEquals(ApiVersion.KOTLIN_1_5, languageVersionSettingsBefore.apiVersion)

            val projectLanguageVersionSettingsBefore = myProject.languageVersionSettings
            Assert.assertEquals(LanguageVersion.KOTLIN_1_5, projectLanguageVersionSettingsBefore.languageVersion)
            Assert.assertEquals(ApiVersion.KOTLIN_1_5, projectLanguageVersionSettingsBefore.apiVersion)

            runWithModalProgressBlocking(project, "") {
                saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
            }
            checkKotlincPresence(true)

            KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
                languageVersion = LanguageVersion.KOTLIN_1_6.versionString
            }

            // Emulate project root change, as after changing Kotlin language settings in the preferences
            runWriteActionAndWait {
                myProject.invalidateProjectRoots(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
            }

            val languageVersionSettingsAfter = module.languageVersionSettings
            Assert.assertEquals(LanguageVersion.KOTLIN_1_6, languageVersionSettingsAfter.languageVersion)
            Assert.assertEquals(ApiVersion.KOTLIN_1_5, languageVersionSettingsAfter.apiVersion)

            val projectLanguageVersionSettingsAfter = myProject.languageVersionSettings
            Assert.assertEquals(LanguageVersion.KOTLIN_1_6, projectLanguageVersionSettingsAfter.languageVersion)
            Assert.assertEquals(ApiVersion.KOTLIN_1_5, projectLanguageVersionSettingsAfter.apiVersion)
        }

        assertEquals(
            "Update your code to replace the use of deprecated language and library features with supported constructs<br/><br/>Detected migration:<br/>&nbsp;&nbsp;Language version: 1.5 to 1.6<br/>",
            notificationText,
        )
    }

    fun testTwoModulesWithNonDefaultPath_doNotCopyInDefault() {
        doTestConfigureModulesWithNonDefaultSetup(jvmConfigurator)
        assertEmpty(getCanBeConfiguredModules(myProject, jsConfigurator))
    }

    // The JS configurator does not work with the new klib format because it requires target platforms to be set correctly
    //fun testTwoModulesWithJSNonDefaultPath_doNotCopyInDefault() {
    //    doTestConfigureModulesWithNonDefaultSetup(jsConfigurator)
    //    assertEmpty(getCanBeConfiguredModules(myProject, jvmConfigurator))
    //}

    fun testModuleFacetChange() {
        val kotlinFacet = KotlinFacet.get(module)!!
        val languageVersionSettings = module.languageVersionSettings
        assertEquals(languageVersionSettings, module.languageVersionSettings)

        runWriteAction {
            val model = FacetManager.getInstance(module).createModifiableModel()
            model.removeFacet(kotlinFacet)
            model.commit()
        }

        assertFalse(languageVersionSettings == module.languageVersionSettings)
    }

    fun testSimple() {
        assertNotConfigured(module, jvmConfigurator)
        jvmConfigurator.configure(myProject, emptyList())
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        assertProperlyConfigured(module, jvmConfigurator)
    }

    fun testNoKotlincExistsNoSettingsLatestRuntime() {
        val expectedLanguageVersion = KotlinPluginLayout.standaloneCompilerVersion.languageVersion
        Assert.assertEquals(expectedLanguageVersion, module.languageVersionSettings.languageVersion)
        Assert.assertEquals(expectedLanguageVersion, myProject.languageVersionSettings.languageVersion)
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
        checkKotlincPresence(false) // TODO: replace to "jpsVersionOnly = true" after KTI-724
    }

    fun testKotlincExistsNoSettingsLatestRuntimeNoVersionAutoAdvance() {
        val expectedLanguageVersion = KotlinPluginLayout.standaloneCompilerVersion.languageVersion
        Assert.assertEquals(expectedLanguageVersion, module.languageVersionSettings.languageVersion)
        Assert.assertEquals(expectedLanguageVersion, myProject.languageVersionSettings.languageVersion)
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            autoAdvanceLanguageVersion = false
            autoAdvanceApiVersion = false
        }
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
        checkKotlincPresence()
    }

    fun testDropKotlincOnVersionAutoAdvance() {
        Assert.assertEquals(LanguageVersion.KOTLIN_1_4, module.languageVersionSettings.languageVersion)
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            autoAdvanceLanguageVersion = true
            autoAdvanceApiVersion = true
        }
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
        checkKotlincPresence(false) // TODO: replace to "jpsVersionOnly = true" after KTI-724
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
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
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
        val settings = myProject.languageVersionSettings
        Assert.assertEquals(ApiVersion.KOTLIN_1_1, settings.apiVersion)
    }

    fun testNoKotlincExistsNoSettingsLatestRuntimeNullizeEmptyStrings() {
        Kotlin2JsCompilerArgumentsHolder.getInstance(project).update {
            sourceMapPrefix = ""
            sourceMapEmbedSources = ""
        }
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
        checkKotlincPresence(false) // TODO: replace to "jpsVersionOnly = true" after KTI-724
    }

    private fun doTestLoadAndSaveProjectWithFacetConfig(valueBefore: String, valueAfter: String) {
        val facetManager = FacetManager.getInstance(module)
        val moduleFileContentBefore = moduleFileContent()
        Assert.assertTrue(moduleFileContentBefore.contains(valueBefore))
        facetManager.allFacets.forEach { facetManager.facetConfigurationChanged(it) }
        runWithModalProgressBlocking(project, "") {
            saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = myProject)
        }
        val moduleFileContentAfter = moduleFileContent()
        Assert.assertEquals(moduleFileContentBefore.replace(valueBefore, valueAfter), moduleFileContentAfter)
    }

}
