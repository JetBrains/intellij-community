// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.ErrorReporter
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class AllIntellijEntitiesGenerationTest : CodeGenerationTestBase() {
  private val LOG = logger<AllIntellijEntitiesGenerationTest>()

  private val MODULES_WITH_UNKNOWN_FIELDS: Set<String> = setOf("intellij.platform.workspaceModel.storage.testEntities")

  private val SKIPPED_MODULE_PATHS: Set<Pair<String, String>> = setOf(
    "intellij.platform.workspaceModel.storage.tests" to
      "com/intellij/workspaceModel/storage",
    "intellij.platform.workspaceModel.storage.testEntities" to
      "com/intellij/workspaceModel/storage/entities/unknowntypes/test/api",
    "intellij.platform.workspaceModel.storage.testEntities" to
      "com/intellij/workspaceModel/storage/entities/model/api",
    "intellij.platform.workspaceModel.storage" to
      "com/intellij/workspaceModel/storage"
  )

  override fun setUp() {
    CodeGeneratorVersions.checkApiInInterface = false
    CodeGeneratorVersions.checkApiInImpl = false
    CodeGeneratorVersions.checkImplInImpl = false
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    CodeGeneratorVersions.checkApiInInterface = true
    CodeGeneratorVersions.checkApiInImpl = true
    CodeGeneratorVersions.checkImplInImpl = true
  }

  override val testDataDirectory: File
    get() = File(IdeaTestExecutionPolicy.getHomePathWithPolicy())

  override val shouldAddWorkspaceStorageLibrary: Boolean
    get() = true

  @Test
  fun `test generation of all entities in intellij codebase`() {
    val regex = Regex("interface [a-zA-Z0-9]+\\s*:\\s*WorkspaceEntity[a-zA-Z0-9]*")
    val storage = runBlocking { loadProjectIntellijProject() }

    val modulesToCheck = mutableSetOf<Triple<ModuleEntity, SourceRootEntity, String>>()
    storage.entities(SourceRootEntity::class.java).forEach { sourceRoot ->
      val moduleEntity = sourceRoot.contentRoot.module

      File(sourceRoot.url.presentableUrl).walk().forEach {
        if (it.isFile && it.extension == "kt") {
          if (regex.containsMatchIn(it.readText())) {
            val relativePath = Path.of(sourceRoot.url.presentableUrl).relativize(Path.of(it.parent)).toString()
            if (moduleEntity.name to relativePath !in SKIPPED_MODULE_PATHS) {
              modulesToCheck.add(Triple(moduleEntity, sourceRoot, relativePath))
            }
          }
        }
      }
    }

    modulesToCheck.forEach { (moduleEntity, sourceRoot, pathToPackage) ->
      when (moduleEntity.name) {
        "intellij.platform.workspaceModel.storage" -> {
          runWriteActionAndWait {
            val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
            removeWorkspaceStorageLibrary(modifiableModel)
            modifiableModel.commit()
          }
          generateAndCompare(moduleEntity, sourceRoot, pathToPackage)
          runWriteActionAndWait {
            val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
            addWorkspaceStorageLibrary(modifiableModel)
            modifiableModel.commit()
          }
        }
        in MODULES_WITH_UNKNOWN_FIELDS -> {
          generateAndCompare(moduleEntity, sourceRoot, pathToPackage, true)
        }
        else -> {
          generateAndCompare(moduleEntity, sourceRoot, pathToPackage)
        }
      }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun generateAndCompare(moduleEntity: ModuleEntity, sourceRoot: SourceRootEntity, pathToPackage: String, keepUnknownFields: Boolean = false) {
    val genSourceRoots = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.filter { it.generated }
    val relativize = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl))
    myFixture.copyDirectoryToProject(relativize.toString(), "")
    val apiRootPath = Path.of(sourceRoot.url.presentableUrl, pathToPackage)
    val implRootPath = Path.of(genSourceRoots.first().sourceRoot.url.presentableUrl, pathToPackage)
    LOG.info("Generating entities for module: ${moduleEntity.name}")
    generateAndCompare(apiRootPath, implRootPath, keepUnknownFields, pathToPackage)
    (tempDirFixture as LightTempDirTestFixtureImpl).deleteAll()
  }

  private suspend fun loadProjectIntellijProject(): MutableEntityStorage {
    val mutableEntityStorage = MutableEntityStorage.create()
    val virtualFileManager = IdeVirtualFileUrlManagerImpl()
    val projectDir = testDataDirectory.toVirtualFileUrl(virtualFileManager)

    val directoryBased = JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))
    JpsProjectEntitiesLoader.loadProject(configLocation = directoryBased, builder = mutableEntityStorage,
                                         externalStoragePath = Paths.get("/tmp"), errorReporter = TestErrorReporter,
                                         virtualFileManager = virtualFileManager)
    return mutableEntityStorage
  }

  internal object TestErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw AssertionFailedError("Failed to load ${file.url}: $message")
    }
  }
}

