// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.PathManager.getSystemDir
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTests
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.testFramework.*
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.UsefulTestCase.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetBridgeFactory
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetConfigurationBridge
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.junit.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class KotlinFacetDeserializationTest {
    companion object {
        @JvmField
        @ClassRule
        val appRule = ApplicationRule()
    }

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    @JvmField
    @Rule
    val tempDirManager = TemporaryDirectory()

    @Before
    fun setUp() {
        Assume.assumeTrue("Execute only if kotlin facet bridge enabled", KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled)
    }

    @Test
    fun simpleProjectDeserialization() {
        loadProjectAndCheckResults("simpleProject", false) { project ->
            val moduleManager = ModuleManager.getInstance(project)
            val facetManager = FacetManager.getInstance(moduleManager.modules[0])
            assertSize(1, facetManager.allFacets)
            val facetBridge = facetManager.allFacets[0]
            assertEquals("Kotlin", facetBridge.name)

            val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
            val kotlinSettingsEntities = entityStorage.entities(KotlinSettingsEntity::class.java).toList()
            assertSize(1, kotlinSettingsEntities)

            val kotlinSettingsEntity = kotlinSettingsEntities[0]
            with(kotlinSettingsEntity) {
                assertEquals(
                    JpsProjectFileEntitySource.FileInDirectory::class.java,
                    entitySource::class.java
                )
                assertEquals("Kotlin", name)
                assertNotNull(module)
                assertTrue(useProjectSettings)
                assertEmpty(implementedModuleNames)
                assertEmpty(additionalVisibleModuleNames)
                assertEmpty(externalProjectId)
                assertEquals(KotlinModuleKind.DEFAULT, kind)
                assertEmpty(compilerArguments)
            }

            val facetConfiguration = facetBridge.configuration as KotlinFacetConfigurationBridge
            with(facetConfiguration.settings) {
                assertTrue(useProjectSettings)
                assertEmpty(implementedModuleNames)
                assertEmpty(additionalVisibleModuleNames)
                assertEmpty(externalProjectId)
                assertEquals(KotlinModuleKind.DEFAULT, kind)
            }
        }
    }

    @Test
    fun projectWithDetailsDeserialization() {
        loadProjectAndCheckResults("projectWithDetails", false) { project ->
            val moduleManager = ModuleManager.getInstance(project)
            val facetManager = FacetManager.getInstance(moduleManager.modules[0])
            assertSize(1, facetManager.allFacets)
            val facetBridge = facetManager.allFacets[0]
            assertEquals("Kotlin", facetBridge.name)

            val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
            val kotlinSettingsEntities = entityStorage.entities(KotlinSettingsEntity::class.java).toList()
            assertSize(1, kotlinSettingsEntities)

            checkComplexProjectConfiguration(kotlinSettingsEntities, facetBridge)
        }
    }

    @Test
    fun projectFromExternalSystemDeserialization() {
        loadProjectAndCheckResults("projectFromExternalSystem", true) { project ->
            val moduleManager = ModuleManager.getInstance(project)
            val facetManager = FacetManager.getInstance(moduleManager.modules[0])
            assertSize(1, facetManager.allFacets)
            val facetBridge = facetManager.allFacets[0]
            assertEquals("Kotlin", facetBridge.name)

            val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
            val kotlinSettingsEntities = entityStorage.entities(KotlinSettingsEntity::class.java).toList()
            assertSize(1, kotlinSettingsEntities)

            checkComplexProjectConfiguration(kotlinSettingsEntities, facetBridge)
        }
    }

    private fun checkComplexProjectConfiguration(kotlinSettingsEntities: List<KotlinSettingsEntity>, facetBridge: Facet<*>) {
        val kotlinSettingsEntity = kotlinSettingsEntities[0]
        with(kotlinSettingsEntity) {
            assertEquals(
                JpsProjectFileEntitySource.FileInDirectory::class.java,
                entitySource::class.java
            )
            assertEquals("Kotlin", name)
            assertEmpty(sourceRoots)
            assertFalse(useProjectSettings)
            assertTrue(isHmppEnabled)
            assertEquals(kind, KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER)
            assertEquals(listOf("implementedModule1", "implementedModule2"), implementedModuleNames)
            assertEquals(listOf("dependsOnModule1", "dependsOnModule2"), dependsOnModuleNames)
            assertEquals(setOf("friend1", "friend2"), additionalVisibleModuleNames)
            assertEmpty(sourceSetNames)
            assertEmpty(externalProjectId)
            assertEmpty(testOutputPath)
        }

        val facetConfiguration = facetBridge.configuration as KotlinFacetConfigurationBridge
        with(facetConfiguration.settings) {
            assertFalse(useProjectSettings)
            assertTrue(isHmppEnabled)
            assertEquals(kind, KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER)
            with(compilerArguments!!) {
                assertEquals(LanguageVersion.KOTLIN_1_0.versionString, apiVersion)
                assertEquals(LanguageVersion.KOTLIN_1_3.versionString, languageVersion)
            }
            assertEquals(listOf("implementedModule1", "implementedModule2"), implementedModuleNames)
            assertEquals(listOf("dependsOnModule1", "dependsOnModule2"), dependsOnModuleNames)
            assertEquals(setOf("friend1", "friend2"), additionalVisibleModuleNames)
            assertEmpty(sourceSetNames)
            assertEmpty(externalProjectId)
            assertEmpty(testOutputPath)
        }
    }

    private val testDataPath = "/plugins/kotlin/base/facet/testData/"

    private val testDataRoot
        get() = Paths.get(
            getCommunityPath().replace(File.separatorChar, '/') + testDataPath
        ).resolve(
            "workspaceModel"
        )

    private fun loadProjectAndCheckResults(testDataDirName: String, externalSystem: Boolean, checkProject: (Project) -> Unit) {
        fun copyProjectFiles(dir: VirtualFile): Path {
            val projectDir = dir.toNioPath()
            if (!externalSystem) {
                getCommunityPath()
                val testProjectFilesDir = testDataRoot.resolve(testDataDirName).toFile()
                if (testProjectFilesDir.exists()) {
                    FileUtil.copyDir(testProjectFilesDir, projectDir.toFile())
                }
            } else {
                val testProjectFilesDir = testDataRoot.resolve(testDataDirName).resolve("internal").toFile()
                if (testProjectFilesDir.exists()) {
                    FileUtil.copyDir(testProjectFilesDir, projectDir.toFile())
                }
                val testCacheFilesDir = testDataRoot.resolve(testDataDirName).resolve("external").toFile()
                if (testCacheFilesDir.exists()) {
                    val cachePath = getSystemDir()
                      .resolve("projects")
                        .resolve(getProjectCacheFileName(dir.toNioPath()))
                        .resolve("external_build_system")
                    FileUtil.copyDir(testCacheFilesDir, cachePath.toFile())
                }
            }
            VfsUtil.markDirtyAndRefresh(false, true, true, dir)
            return projectDir
        }
        doNotEnableExternalStorageByDefaultInTests {
            runBlocking {
                createOrLoadProject(tempDirManager, ::copyProjectFiles, loadComponentState = true, useDefaultProjectSettings = false) {
                    checkProject(it)
                }
            }
        }
    }
}
