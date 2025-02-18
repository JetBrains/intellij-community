// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator.Companion.modulesWithAbstractTypes
import com.intellij.idea.IJIgnore
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializers
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializersImpl
import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.util.SystemProperties
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.SerializationContextForTests
import com.intellij.workspaceModel.ide.impl.jps.serialization.saveAffectedEntities
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

abstract class AbstractAllIntellijEntitiesGenerationTest : CodeGenerationTestBase() {
  private val LOG = logger<AbstractAllIntellijEntitiesGenerationTest>()

  private val virtualFileManager = IdeVirtualFileUrlManagerImpl()

  private val skippedModulePaths: Set<Pair<String, Path>> = setOf(
    "intellij.platform.workspace.storage.tests" to
      Paths.get("com/intellij/platform/workspace/storage/tests"),
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

  // IJPL-895
  fun `_test generation of all entities in intellij codebase`() {
    executeEntitiesGeneration(::generateAndCompare)
  }

  @IJIgnore(issue = "IDEA-364751")
  fun `test update code`() {
    val propertyKey = "intellij.workspace.model.update.entities"
    if (!SystemProperties.getBooleanProperty(propertyKey, false)) {
      println("Set ${propertyKey} system property to 'true' to update entities code in the sources")
      return
    }

    executeEntitiesGeneration(::generate)
  }

  private fun executeEntitiesGeneration(
    generationFunction: (MutableEntityStorage, ModuleEntity, SourceRootEntity, Set<String>, Boolean, Boolean, Boolean) -> Boolean
  ) {
    // TODO :: Fix detection of entities in modules
    val regexToDetectWsmClasses = Regex(mergePatterns(
      // Regex for searching entities that implements `ModuleSettingsFacetBridgeEntity`
      "interface [a-zA-Z0-9]+\\s*:\\s*ModuleSettingsFacetBridgeEntity[a-zA-Z0-9]*",
      // Regex for searching regular entities in modules
      "interface [a-zA-Z0-9]+\\s*:\\s*WorkspaceEntity[a-zA-Z0-9]*",
      // Regex for searching entity source implementations in modules
      "(class|object) [a-zA-Z0-9]+\\s*(\\(.*\\))?\\s*:\\s*EntitySource",
      // Regex for searching symbolic id implementations in modules
      "(class|object) [a-zA-Z0-9]+\\s*(\\(.*\\))?\\s*:\\s*SymbolicEntityId<[a-zA-Z0-9]*>"
    ))

    val regexToDetectOutdatedGeneratedFiles = Regex(
      // Regex for searching generated implementations of MetadataStorage
      "object [a-zA-Z0-9]+\\s*(\\(.*\\))?\\s*:\\s*MetadataStorage\\s*\\{"
    )

    val (storage, jpsProjectSerializer) = runBlocking { loadProjectIntellijProject() }

    val filesToRemove = arrayListOf<File>()
    val modulesToCheck = mutableMapOf<Pair<ModuleEntity, SourceRootEntity>, MutableSet<String>>()

    storage.entities(SourceRootEntity::class.java).forEach { sourceRoot ->
      val moduleEntity = sourceRoot.contentRoot.module

      val relativePaths: MutableSet<Path> = hashSetOf()
      File(sourceRoot.url.presentableUrl).walk().forEach { file ->
        if (file.isFile && file.extension == "kt") {
          processFileIfMatch(file, regexToDetectWsmClasses) {
            val relativePath = Path.of(sourceRoot.url.presentableUrl).relativize(Path.of(it.parent))
            if (moduleEntity.name to relativePath !in skippedModulePaths) {
              relativePaths.add(relativePath)
            }
          }

          processFileIfMatch(file, regexToDetectOutdatedGeneratedFiles) {
            filesToRemove.add(it)
          }
        }
      }

      if (relativePaths.isNotEmpty()) {
        modulesToCheck.getOrPut(moduleEntity to sourceRoot) { mutableSetOf() }
          .addAll(relativePaths.onlySubpaths().map { it.invariantSeparatorsPathString })
      }
    }

    //Remove outdated files (MetadataStorageImpl)
    filesToRemove.forEach { FileUtil.delete(it) } //TODO(Delete all files, if generation was successful)
    //TODO(Delete empty folder)

    var storageChanged = false
    modulesToCheck.forEach { (moduleEntity, sourceRoot), pathToPackages ->
      val isTestModule = sourceRoot.rootTypeId == JAVA_TEST_ROOT_ENTITY_TYPE_ID

      fun generateForSpecificModule(
        processAbstractTypes: Boolean, beforeGeneration: (ModifiableRootModel) -> Unit,
        afterGeneration: (ModifiableRootModel) -> Unit,
      ) {
        runWriteActionAndWait {
          val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
          beforeGeneration(modifiableModel)
          modifiableModel.commit()
        }
        val projectModelUpdateResult = generationFunction(storage, moduleEntity, sourceRoot, pathToPackages, processAbstractTypes, false, isTestModule)
        storageChanged = storageChanged || projectModelUpdateResult
        runWriteActionAndWait {
          val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
          afterGeneration(modifiableModel)
          modifiableModel.commit()
        }
      }

      when (moduleEntity.name) {
        "intellij.platform.workspace.storage" -> {
          generateForSpecificModule(false, { modifiableModel -> removeWorkspaceStorageLibrary(modifiableModel) },
                                    { modifiableModel -> addWorkspaceStorageLibrary(modifiableModel) })
        }
        "intellij.platform.workspace.jps" -> {
          generateForSpecificModule(false, { modifiableModel -> removeWorkspaceJpsEntitiesLibrary(modifiableModel) },
                                    { modifiableModel -> addWorkspaceJpsEntitiesLibrary(modifiableModel) })
        }
        "intellij.javaee.platform", "intellij.javaee.ejb", "intellij.javaee.web" -> {
          // For these modules we need to have reference to `ConfigFileItem` thus we added its module as library
          generateForSpecificModule(false, { modifiableModel -> addIntellijJavaLibrary(modifiableModel) },
                                    { modifiableModel -> removeIntellijJavaLibrary(modifiableModel) })
        }
        "intellij.rider.plugins.unity" -> {
          generateForSpecificModule(true, { modifiableModel -> addRiderPluginLibrary(modifiableModel) },
                                    { modifiableModel -> removeRiderPluginLibrary(modifiableModel) })
        }
        "intellij.rider.rdclient.dotnet" -> {
          generateForSpecificModule(true, { modifiableModel -> addRiderModelLibrary(modifiableModel) },
                                    { modifiableModel -> removeRiderModelLibrary(modifiableModel) })
        }
        "kotlin.base.facet" -> {
          generateForSpecificModule(false, { modifiableModel ->
            addIntellijJavaLibrary(modifiableModel)
            addKotlinJpsCommonJar(modifiableModel)
          }, { modifiableModel ->
                                      removeIntellijJavaLibrary(modifiableModel)
                                      removeKotlinJpsCommonJar(modifiableModel)
                                    })
        }
        in modulesWithAbstractTypes -> {
          val projectModelUpdateResult = generationFunction(storage, moduleEntity, sourceRoot, pathToPackages, true, false, isTestModule)
          storageChanged = storageChanged || projectModelUpdateResult
        }
        else -> {
          val projectModelUpdateResult = generationFunction(storage, moduleEntity, sourceRoot, pathToPackages, false, false, isTestModule)
          storageChanged = storageChanged || projectModelUpdateResult
        }
      }
    }
    if (storageChanged) {
      val affectedEntitySources = modulesToCheck.map { it.key.first.entitySource }.toSet()
      (jpsProjectSerializer as JpsProjectSerializersImpl).saveAffectedEntities(storage, affectedEntitySources, createProjectConfigLocation())
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun generate(
    storage: MutableEntityStorage, moduleEntity: ModuleEntity,
    sourceRoot: SourceRootEntity, pathToPackages: Set<String>,
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean,
    isTestModule: Boolean,
  ): Boolean {
    val relativize = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl))
    myFixture.copyDirectoryToProject(relativize.invariantSeparatorsPathString, "")
    LOG.info("Generating entities for module: ${moduleEntity.name}")
    setupCustomIndent(moduleEntity)

    val result = pathToPackages.map { pathToPackage ->
      val packagePath = pathToPackage.replace(".", "/")

      val (srcRoot, genRoot) = generateCode(
        relativePathToEntitiesDirectory = packagePath,
        processAbstractTypes = processAbstractTypes,
        explicitApiEnabled = explicitApiEnabled,
        isTestModule = isTestModule
      )

      runWriteActionAndWait {
        var storageChanged = false
        val genSourceRoot = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.firstOrNull { it.generated }?.sourceRoot ?: run {
          val genFolderVirtualFile = VfsUtil.createDirectories("${sourceRoot.contentRoot.url.presentableUrl}/${WorkspaceModelGenerator.GENERATED_FOLDER_NAME}")
          val javaSourceRoot = sourceRoot.javaSourceRoots.first()
          val updatedContentRoot = storage.modifyContentRootEntity(sourceRoot.contentRoot) {
            this.sourceRoots += SourceRootEntity(genFolderVirtualFile.toVirtualFileUrl(virtualFileManager),
                                                 sourceRoot.rootTypeId, sourceRoot.entitySource) {
              javaSourceRoots = listOf(
                JavaSourceRootPropertiesEntity(true, javaSourceRoot.packagePrefix, javaSourceRoot.entitySource))
            }
          }
          val result = updatedContentRoot.sourceRoots.last()
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

  private fun generateAndCompare(
    storage: MutableEntityStorage, moduleEntity: ModuleEntity,
    sourceRoot: SourceRootEntity, pathToPackages: Set<String>,
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean,
    isTestModule: Boolean,
  ): Boolean {
    val genSourceRoots = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.filter { it.generated }
    val relativize = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl))
    myFixture.copyDirectoryToProject(relativize.invariantSeparatorsPathString, "")
    LOG.info("Generating entities for module: ${moduleEntity.name}")
    setupCustomIndent(moduleEntity)

    pathToPackages.forEach { pathToPackage ->
      val apiRootPath = Path.of(sourceRoot.url.presentableUrl, pathToPackage)
      val implRootPath = Path.of(genSourceRoots.first().sourceRoot.url.presentableUrl, pathToPackage)
      generateAndCompare(
        dirWithExpectedApiFiles = apiRootPath,
        dirWithExpectedImplFiles = implRootPath,
        pathToPackage = pathToPackage,
        processAbstractTypes = processAbstractTypes,
        explicitApiEnabled = explicitApiEnabled,
        isTestModule = isTestModule
      )
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
    val kotlinFileType: LanguageFileType? = KotlinFileType.INSTANCE
    val indentOptions = settings.getIndentOptions(kotlinFileType)
    indentOptions.INDENT_SIZE = indent
    indentOptions.TAB_SIZE = indent
    indentOptions.CONTINUATION_INDENT_SIZE = indent
  }

  private fun resetCustomIndent(moduleEntity: ModuleEntity) {
    modulesWithCustomIndentSize[moduleEntity.name] ?: return
    val settings = CodeStyle.getSettings(project)
    val kotlinFileType: LanguageFileType? = KotlinFileType.INSTANCE
    val indentOptions = settings.getIndentOptions(kotlinFileType)
    indentOptions.INDENT_SIZE = INDENT_SIZE
    indentOptions.TAB_SIZE = TAB_SIZE
    indentOptions.CONTINUATION_INDENT_SIZE = CONTINUATION_INDENT_SIZE
  }

  private fun createProjectConfigLocation(): JpsProjectConfigLocation {
    val projectDir = testDataDirectory.toVirtualFileUrl(virtualFileManager)
    return JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))
  }

  private fun mergePatterns(vararg patterns: String): String {
    return patterns.joinToString("|") { "($it)" }
  }

  private fun processFileIfMatch(file: File, regex: Regex, function: (File) -> Unit) {
    if (regex.containsMatchIn(file.readText())) {
      function.invoke(file)
    }
  }

  private fun Iterable<Path>.onlySubpaths(): Iterable<Path> {
    return filter { path -> none { anotherPath -> anotherPath != path && anotherPath.isSubPathOf(path) } }
  }

  private fun Path.isSubPathOf(anotherPath: Path): Boolean = anotherPath.startsWith(this)
  
  internal object TestErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw AssertionFailedError("Failed to load ${file.url}: $message")
    }
  }
}