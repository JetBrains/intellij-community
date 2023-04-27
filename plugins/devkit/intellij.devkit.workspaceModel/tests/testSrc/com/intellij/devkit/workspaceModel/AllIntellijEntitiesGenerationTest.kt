// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.util.SystemProperties
import com.intellij.util.io.systemIndependentPath
import com.intellij.platform.workspaceModel.jps.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.*
import com.intellij.workspaceModel.ide.toVirtualFileUrl
import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import com.intellij.workspaceModel.storage.impl.url.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

class AllIntellijEntitiesGenerationTest : CodeGenerationTestBase() {
  private val LOG = logger<AllIntellijEntitiesGenerationTest>()

  private val virtualFileManager = IdeVirtualFileUrlManagerImpl()
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

  private val modulesWithCustomIndentSize: Map<String, Int> = mapOf("kotlin.base.scripting" to 4)

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

  private fun executeEntitiesGeneration(generationFunction: (MutableEntityStorage, ModuleEntity, SourceRootEntity, Set<String>, Boolean) -> Boolean) {
    val regex = Regex("interface [a-zA-Z0-9]+\\s*:\\s*WorkspaceEntity[a-zA-Z0-9]*")
    val (storage, jpsProjectSerializer) = runBlocking { loadProjectIntellijProject() }

    val modulesToCheck = mutableMapOf<Pair<ModuleEntity, SourceRootEntity>, MutableSet<String>>()
    storage.entities(SourceRootEntity::class.java).forEach { sourceRoot ->
      val moduleEntity = sourceRoot.contentRoot.module

      File(sourceRoot.url.presentableUrl).walk().forEach {
        if (it.isFile && it.extension == "kt") {
          if (regex.containsMatchIn(it.readText())) {
            val relativePath = Path.of(sourceRoot.url.presentableUrl).relativize(Path.of(it.parent)).systemIndependentPath
            if (moduleEntity.name to relativePath !in skippedModulePaths) {
              modulesToCheck.getOrPut(moduleEntity to sourceRoot){ mutableSetOf() }.add(relativePath)
            }
          }
        }
      }
    }

    var storageChanged = false
    modulesToCheck.forEach { (moduleEntity, sourceRoot), pathToPackages ->
      when (moduleEntity.name) {
        "intellij.platform.workspaceModel.storage" -> {
          runWriteActionAndWait {
            val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
            removeWorkspaceStorageLibrary(modifiableModel)
            modifiableModel.commit()
          }
          storageChanged = storageChanged || generationFunction(storage, moduleEntity, sourceRoot, pathToPackages, false)
          runWriteActionAndWait {
            val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
            addWorkspaceStorageLibrary(modifiableModel)
            modifiableModel.commit()
          }
        }
        in modulesWithUnknownFields -> {
          storageChanged = storageChanged || generationFunction(storage, moduleEntity, sourceRoot, pathToPackages, true)
        }
        else -> {
          storageChanged = storageChanged || generationFunction(storage, moduleEntity, sourceRoot, pathToPackages, false)
        }
      }
    }
    if (storageChanged) {
      (jpsProjectSerializer as JpsProjectSerializersImpl).saveAllEntities(storage, createProjectConfigLocation())
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun generate(storage: MutableEntityStorage, moduleEntity: ModuleEntity, sourceRoot: SourceRootEntity, pathToPackages: Set<String>,
                       keepUnknownFields: Boolean = false): Boolean {
    val relativize = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl))
    myFixture.copyDirectoryToProject(relativize.systemIndependentPath, "")
    LOG.info("Generating entities for module: ${moduleEntity.name}")
    setupCustomIndent(moduleEntity)

    val result = pathToPackages.map { pathToPackage ->
      val packagePath = pathToPackage.replace(".", "/")
      val (srcRoot, genRoot) = generateCode(packagePath, keepUnknownFields)
      runWriteActionAndWait {
        var storageChanged = false
        val genSourceRoot = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.firstOrNull { it.generated }?.sourceRoot ?: run {
          val genFolderVirtualFile = VfsUtil.createDirectories("${sourceRoot.contentRoot.url.presentableUrl}/${WorkspaceModelGenerator.GENERATED_FOLDER_NAME}")
          val javaSourceRoot = sourceRoot.javaSourceRoots.first()
          val result = storage.addEntity(SourceRootEntity(genFolderVirtualFile.toVirtualFileUrl(virtualFileManager),
                                                          sourceRoot.rootType, sourceRoot.entitySource) {
              contentRoot = sourceRoot.contentRoot
              javaSourceRoots = listOf(
                JavaSourceRootPropertiesEntity(true, javaSourceRoot.packagePrefix, javaSourceRoot.entitySource))
            })
          storageChanged = true
          result
        }

        val apiRootPath = Path.of(sourceRoot.url.presentableUrl, pathToPackage)
        val implRootPath = Path.of(genSourceRoot.url.presentableUrl, pathToPackage)
        val virtualFileManager = VirtualFileManager.getInstance()
        val apiDir = virtualFileManager.refreshAndFindFileByNioPath(apiRootPath)!!
        val implDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(implRootPath) ?: run {
          VfsUtil.createDirectories(implRootPath.pathString)
        }
        VfsUtil.copyDirectory(this, srcRoot, apiDir, VirtualFileFilter { it != genRoot })
        VfsUtil.copyDirectory(this, genRoot, implDir, null)
        storageChanged
      }
    }.any { it }

    resetCustomIndent(moduleEntity)
    (tempDirFixture as LightTempDirTestFixtureImpl).deleteAll()
    return result
  }

  private fun generateAndCompare(storage: MutableEntityStorage, moduleEntity: ModuleEntity, sourceRoot: SourceRootEntity,
                                 pathToPackages: Set<String>, keepUnknownFields: Boolean = false): Boolean {
    val genSourceRoots = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.filter { it.generated }
    val relativize = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl))
    myFixture.copyDirectoryToProject(relativize.systemIndependentPath, "")
    LOG.info("Generating entities for module: ${moduleEntity.name}")
    setupCustomIndent(moduleEntity)

    pathToPackages.forEach { pathToPackage ->
      val apiRootPath = Path.of(sourceRoot.url.presentableUrl, pathToPackage)
      val implRootPath = Path.of(genSourceRoots.first().sourceRoot.url.presentableUrl, pathToPackage)
      generateAndCompare(apiRootPath, implRootPath, keepUnknownFields, pathToPackage)
    }

    resetCustomIndent(moduleEntity)
    (tempDirFixture as LightTempDirTestFixtureImpl).deleteAll()
    return false
  }

  private suspend fun loadProjectIntellijProject(): Pair<MutableEntityStorage, JpsProjectSerializers> {
    val mutableEntityStorage = MutableEntityStorage.create()
    val configLocation = createProjectConfigLocation()
    val context = SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation))
    val jpsProjectSerializer = JpsProjectEntitiesLoader.loadProject(configLocation = configLocation,
                                                                    builder = mutableEntityStorage,
                                                                    orphanage = mutableEntityStorage,
                                                                    externalStoragePath = Paths.get("/tmp"),
                                                                    errorReporter = TestErrorReporter,
                                                                    context = context)
    return mutableEntityStorage to jpsProjectSerializer
  }

  private fun setupCustomIndent(moduleEntity: ModuleEntity) {
    val indent = modulesWithCustomIndentSize[moduleEntity.name] ?: return
    val settings = CodeStyle.getSettings(project)
    val indentOptions = settings.getIndentOptions(KotlinFileType.INSTANCE)
    indentOptions.INDENT_SIZE = indent
    indentOptions.TAB_SIZE = indent
    indentOptions.CONTINUATION_INDENT_SIZE = indent
  }

  private fun resetCustomIndent(moduleEntity: ModuleEntity) {
    modulesWithCustomIndentSize[moduleEntity.name] ?: return
    val settings = CodeStyle.getSettings(project)
    val indentOptions = settings.getIndentOptions(KotlinFileType.INSTANCE)
    indentOptions.INDENT_SIZE = INDENT_SIZE
    indentOptions.TAB_SIZE = TAB_SIZE
    indentOptions.CONTINUATION_INDENT_SIZE = CONTINUATION_INDENT_SIZE
  }

  private fun createProjectConfigLocation(): JpsProjectConfigLocation {
    val projectDir = testDataDirectory.toVirtualFileUrl(virtualFileManager)
    return JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))
  }

  internal object TestErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw AssertionFailedError("Failed to load ${file.url}: $message")
    }
  }
}

