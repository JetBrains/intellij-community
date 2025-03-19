// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.platform.TargetPlatform
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.facet.initializeIfNeeded
import org.jetbrains.kotlin.idea.project.ModulePlatformCache
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.toString

class ModulePlatformCacheTest : KotlinLightCodeInsightFixtureTestCase() {
    val myModule
        get() = myFixture.module
    val Module.targetPlatformFromCache: TargetPlatform
        get() = runReadAction { ModulePlatformCache.getInstance(project)[this] }
    val Module.targetPlatformFromFacet: TargetPlatform?
        get() = runReadAction { kotlinFacet.configuration.settings.targetPlatform }
    val Module.kotlinFacet: KotlinFacet
        get() = FacetManager.getInstance(this).allFacets.firstOrNull() as? KotlinFacet ?: error("Kotlin facet is not configured")

    override fun setUp() {
        super.setUp()
        ModulePlatformCache.getInstance(project) // initialize
        module.createFacetWithAdditionalSetup(JvmPlatforms.defaultJvmPlatform, false) {}
    }

    fun `test target platform cache invalidation on change of jvmTarget - default initial with non-null args`() {
        with(module.kotlinFacet) {
            configuration.settings.targetPlatform = JvmPlatforms.defaultJvmPlatform
            fireFacetChangedEvent(this)

            TestCase.assertNotNull(configuration.settings.compilerArguments)

            updateCompilerArgumentsAndCheck("17")
            updateCompilerArgumentsAndCheck("1.8")
            updateCompilerArgumentsAndCheck("17")
        }
    }

    fun `test target platform cache invalidation on change of jvmTarget - custom initial with non-null args and same first change`() {
        with(module.kotlinFacet) {
            configuration.settings.targetPlatform = JvmPlatforms.jvm17
            fireFacetChangedEvent(this)

            TestCase.assertNotNull(configuration.settings.compilerArguments)

            updateCompilerArgumentsAndCheck("17")
            updateCompilerArgumentsAndCheck("1.8")
            updateCompilerArgumentsAndCheck("17")
        }
    }

    fun `test target platform cache invalidation on change of jvmTarget - custom initial with non-null args and different first change`() {
        with(module.kotlinFacet) {
            configuration.settings.targetPlatform = JvmPlatforms.jvm11
            fireFacetChangedEvent(this)

            TestCase.assertNotNull(configuration.settings.compilerArguments)

            updateCompilerArgumentsAndCheck("17")
            updateCompilerArgumentsAndCheck("1.8")
            updateCompilerArgumentsAndCheck("17")
        }
    }

    fun `test target platform cache invalidation on change of jvmTarget - default initial with null args`() {
        with(module.kotlinFacet) {
            configuration.settings.targetPlatform = JvmPlatforms.defaultJvmPlatform
            configuration.settings.compilerArguments = null
            fireFacetChangedEvent(this)

            TestCase.assertNull(configuration.settings.compilerArguments)

            configuration.settings.targetPlatform = JvmPlatforms.defaultJvmPlatform
            fireFacetChangedEvent(this)
            TestCase.assertEquals(module.targetPlatformFromFacet.toString(), module.targetPlatformFromCache.toString())

            updateTargetPlatformAndCheck(JvmTarget.JVM_1_8)
            updateTargetPlatformAndCheck(JvmTarget.JVM_17)
            updateTargetPlatformAndCheck(JvmTarget.JVM_1_8)
        }
    }

    fun `test target platform cache invalidation on change of jvmTarget - custom initial with null args`() {
        with(module.kotlinFacet) {
            configuration.settings.targetPlatform = JvmPlatforms.defaultJvmPlatform
            configuration.settings.compilerArguments = null
            fireFacetChangedEvent(this)

            TestCase.assertNull(configuration.settings.compilerArguments)

            configuration.settings.targetPlatform = JvmPlatforms.defaultJvmPlatform
            fireFacetChangedEvent(this)
            TestCase.assertEquals(module.targetPlatformFromFacet.toString(), module.targetPlatformFromCache.toString())

            updateTargetPlatformAndCheck(JvmTarget.JVM_17)
            updateTargetPlatformAndCheck(JvmTarget.JVM_1_8)
            updateTargetPlatformAndCheck(JvmTarget.JVM_17)
        }
    }

    private fun fireFacetChangedEvent(mainFacet: KotlinFacet) {
        val allFacets = FacetManager.getInstance(myModule).allFacets
        UsefulTestCase.assertSize(1, allFacets)
        TestCase.assertSame(mainFacet, allFacets[0])

        allFacets.forEach { facet -> FacetManager.getInstance(myModule).facetConfigurationChanged(facet) }
    }

    private fun Module.createFacetWithAdditionalSetup(
        platformKind: TargetPlatform?,
        useProjectSettings: Boolean,
        additionalSetup: IKotlinFacetSettings.() -> Unit
    ) {
        WriteAction.run<Throwable> {
            val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
            with(getOrCreateFacet(modelsProvider, useProjectSettings).configuration.settings) {
                initializeIfNeeded(
                    this@createFacetWithAdditionalSetup,
                    modelsProvider.getModifiableRootModel(this@createFacetWithAdditionalSetup),
                    platformKind
                )
                additionalSetup()
            }
            modelsProvider.commit()
        }
    }

    private fun KotlinFacet.updateCompilerArgumentsAndCheck(jvmTarget: String) {
        this.configuration.settings.updateCompilerArguments {
            (this as K2JVMCompilerArguments).jvmTarget = jvmTarget
        }
        fireFacetChangedEvent(this)
        TestCase.assertEquals(module.targetPlatformFromFacet.toString(), module.targetPlatformFromCache.toString())
    }

    private fun KotlinFacet.updateTargetPlatformAndCheck(jvmTarget: JvmTarget) {
        configuration.settings.targetPlatform = JvmPlatforms.jvmPlatformByTargetVersion(jvmTarget)
        fireFacetChangedEvent(this)
        TestCase.assertEquals(module.targetPlatformFromFacet.toString(), module.targetPlatformFromCache.toString())
    }
}