// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.util.SystemProperties
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
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class AllIntellijEntitiesGenerationTest : CodeGenerationTestBase() {
  private val LOG = logger<AllIntellijEntitiesGenerationTest>()

  private val modulesWithUnknownFields: Set<String> = setOf("intellij.platform.workspaceModel.storage.testEntities")

  private val skippedModulePaths: Set<Pair<String, String>> = setOf(
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

  fun `test generation of all entities in intellij codebase`() {
    executeEntitiesGeneration(::generateAndCompare)
  }

  fun `test update code`() {
    val propertyKey = "intellij.workspace.model.update.entities"
    if (!SystemProperties.getBooleanProperty(propertyKey, false)) {
      println("Set ${propertyKey} system property to 'true' to update entities code in the sources")
      return
    }

    executeEntitiesGeneration(::generate)
  }

  private fun executeEntitiesGeneration(generationFunction: (ModuleEntity, SourceRootEntity, String, Boolean) -> Unit) {
    val regex = Regex("interface [a-zA-Z0-9]+\\s*:\\s*WorkspaceEntity[a-zA-Z0-9]*")
    val storage = runBlocking { loadProjectIntellijProject() }

    val modulesToCheck = mutableSetOf<Triple<ModuleEntity, SourceRootEntity, String>>()
    storage.entities(SourceRootEntity::class.java).forEach { sourceRoot ->
      val moduleEntity = sourceRoot.contentRoot.module

      File(sourceRoot.url.presentableUrl).walk().forEach {
        if (it.isFile && it.extension == "kt") {
          if (regex.containsMatchIn(it.readText())) {
            val relativePath = Path.of(sourceRoot.url.presentableUrl).relativize(Path.of(it.parent)).toString()
            if (moduleEntity.name to relativePath !in skippedModulePaths) {
              modulesToCheck.add(Triple(moduleEntity, sourceRoot, relativePath))
            }
          }
        }
      }
    }

    modulesToCheck.forEach {(moduleEntity, sourceRoot, pathToPackage) ->
      when (moduleEntity.name) {
        "intellij.platform.workspaceModel.storage" -> {
          runWriteActionAndWait {
            val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
            removeWorkspaceStorageLibrary(modifiableModel)
            modifiableModel.commit()
          }
          generationFunction(moduleEntity, sourceRoot, pathToPackage, false)
          runWriteActionAndWait {
            val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
            addWorkspaceStorageLibrary(modifiableModel)
            modifiableModel.commit()
          }
        }
        in modulesWithUnknownFields -> {
          generationFunction(moduleEntity, sourceRoot, pathToPackage, true)
        }
        else -> {
          generationFunction(moduleEntity, sourceRoot, pathToPackage, false)
        }
      }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun generate(moduleEntity: ModuleEntity, sourceRoot: SourceRootEntity, pathToPackage: String, keepUnknownFields: Boolean = false) {
    val packagePath = pathToPackage.replace(".", "/")
    val relativize = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl))
    myFixture.copyDirectoryToProject(relativize.toString(), "")
    LOG.info("Generating entities for module: ${moduleEntity.name}")
    val (srcRoot, genRoot) = generateCode(packagePath, keepUnknownFields)
    runWriteActionAndWait {
      val genSourceRoots = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.filter { it.generated }
      val apiRootPath = Path.of(sourceRoot.url.presentableUrl, pathToPackage)
      val implRootPath = Path.of(genSourceRoots.first().sourceRoot.url.presentableUrl, pathToPackage)
      val apiDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(apiRootPath)!!
      val implDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(implRootPath)!!
      VfsUtil.copyDirectory(this, srcRoot, apiDir, VirtualFileFilter { it != genRoot })
      VfsUtil.copyDirectory(this, genRoot, implDir, null)
    }
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

