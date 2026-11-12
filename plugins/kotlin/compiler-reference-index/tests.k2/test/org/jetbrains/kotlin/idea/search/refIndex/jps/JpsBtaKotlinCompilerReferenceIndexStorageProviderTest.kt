// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.jps

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_DATA_CONTAINER_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaKotlinCompilerReferenceIndexApplicabilityProvider
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaTestFixtureSupport
import org.jetbrains.kotlin.idea.search.refIndex.bta.isBtaCriProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories

@OptIn(ExperimentalBuildToolsApi::class)
@TestApplication
internal class JpsBtaKotlinCompilerReferenceIndexStorageProviderTest {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val project
        get() = projectFixture.get()

    @TestDisposable
    private lateinit var disposable: Disposable

    @Test
    fun `test the key on with JPS cri data picks the JPS BTA provider`() {
        enableBuildToolsApi()
        copySampleCriIntoTargetDataRoot()

        val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)

        assertTrue(provider.isJpsBtaCriProvider())
    }

    @Test
    fun `test createStorage serves lookups and subtypes from the JPS cri roots`() {
        enableBuildToolsApi()
        copySampleCriIntoTargetDataRoot()
        createFixtureSourceFiles()

        val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)!!
        assertTrue(provider.hasIndex(project))
        val storage = provider.createStorage(project, project.basePath!!)!!

        try {
            val usageFileNames = storage.getUsages(BtaTestFixtureSupport.animalFqName).map { it.name }.toSet()
            assertEquals(setOf("Animal.kt", "Dog.kt", "Cat.kt", "Main.kt"), usageFileNames)

            val subtypes = storage.getSubtypesOf(BtaTestFixtureSupport.animalFqName, deep = true).toSet()
            assertEquals(BtaTestFixtureSupport.animalDeepSubtypes, subtypes)
        } finally {
            storage.close()
        }
    }

    @Test
    fun `test the key on with JPS cri data wins over the old and external providers`() {
        enableBuildToolsApi()
        registerExternalBuildSystem()
        createOldJpsDataContainer()
        copySampleCriIntoTargetDataRoot()

        val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)

        assertTrue(provider.isJpsBtaCriProvider())
    }

    @Test
    fun `test the key on with an empty cri directory picks the old JPS provider`() {
        enableBuildToolsApi()
        createOldJpsDataContainer()
        targetDataRoot().resolve(CriToolchain.DATA_PATH).createDirectories()

        val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)

        assertPicksOldJpsProvider(provider)
    }

    @Test
    fun `test the key on without JPS cri data picks the old JPS provider`() {
        enableBuildToolsApi()
        createOldJpsDataContainer()

        val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)

        assertPicksOldJpsProvider(provider)
    }

    @Test
    fun `test the key off with old JPS data picks the old JPS provider`() {
        Registry.get("kotlin.jps.use.build.tools.api").setValue(false, disposable)
        createOldJpsDataContainer()

        val provider = KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project)

        assertPicksOldJpsProvider(provider)
    }

    // The old JPS provider is the only other registered provider in tests, so ruling out the BTA providers names it.
    private fun assertPicksOldJpsProvider(provider: KotlinCompilerReferenceIndexStorageProvider?) {
        assertNotNull(provider)
        assertFalse(provider.isJpsBtaCriProvider())
        assertFalse(provider.isBtaCriProvider())
    }

    private fun enableBuildToolsApi() {
        Registry.get("kotlin.jps.use.build.tools.api").setValue(true, disposable)
        // The bundled Kotlin 2.4.21 is below the version gate, so the tests pin a version that supports the BTA runner
        ApplicationManager.getApplication().invokeAndWait {
            runWriteAction { KotlinJpsPluginSettings.getInstance(project).setVersion("2.5.20") }
        }
    }

    private fun registerExternalBuildSystem() {
        ApplicationManager.getApplication().extensionArea
            .getExtensionPoint<BtaKotlinCompilerReferenceIndexApplicabilityProvider>(BTA_CRI_APPLICABILITY_EP_NAME)
            .registerExtension(object : BtaKotlinCompilerReferenceIndexApplicabilityProvider {
                override fun isApplicable(project: Project): Boolean = true
            }, disposable)
    }

    private fun copySampleCriIntoTargetDataRoot() {
        BtaTestFixtureSupport.copyFixtureInto(targetDataRoot().resolve(CriToolchain.DATA_PATH))
    }

    private fun createFixtureSourceFiles() {
        runWriteAction {
            val sourceRoot = VfsUtil.createDirectories("${project.basePath}/src/main/kotlin/fixtures")
            for (path in BtaTestFixtureSupport.animalLookupPaths) {
                sourceRoot.createChildData(this, path.substringAfterLast('/'))
            }
        }
    }

    private fun createOldJpsDataContainer() {
        val buildDataPaths = BuildDataPathsImpl(BuildManager.getInstance().getProjectSystemDir(project))
        buildDataPaths.targetsDataRoot
            .resolve(KOTLIN_DATA_CONTAINER_ID)
            .resolve(KOTLIN_DATA_CONTAINER_ID)
            .createDirectories()
    }

    private fun targetDataRoot(): Path {
        val buildDataPaths = BuildDataPathsImpl(BuildManager.getInstance().getProjectSystemDir(project))
        // The walk reads every entry under the target-type root, so the directory name is arbitrary
        return buildDataPaths.getTargetTypeDataRootDir(JavaModuleBuildTargetType.PRODUCTION)
            .resolve("m")
            .createDirectories()
    }
}

// The name is a literal because the extension point's EP_NAME constant is internal to the production module
private const val BTA_CRI_APPLICABILITY_EP_NAME = "org.jetbrains.kotlin.btaKotlinCompilerReferenceIndexApplicabilityProvider"
