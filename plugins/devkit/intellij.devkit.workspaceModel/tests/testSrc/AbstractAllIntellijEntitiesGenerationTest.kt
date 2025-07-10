// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.application.options.CodeStyle
import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator.Companion.RIDER_MODULES_PREFIX
import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator.Companion.modulesWithAbstractTypes
import com.intellij.idea.IJIgnore
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializers
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectSerializersImpl
import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.SystemProperties
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
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
import com.intellij.testFramework.workspaceModel.updateProjectModel

abstract class AbstractAllIntellijEntitiesGenerationTest : CodeGenerationTestBase() {
  private val LOG = logger<AbstractAllIntellijEntitiesGenerationTest>()

  private val virtualFileManager = IdeVirtualFileUrlManagerImpl()

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

  open fun `test generation of all entities in intellij codebase`() {
    executeWorkspaceCodeGeneration(::compareIntellijWorkspaceCode)
  }

  @IJIgnore(issue = "IDEA-364751")
  fun `test update code`() {
    if (!SystemProperties.getBooleanProperty(UPDATE_PROPERTY_KEY, false)) {
      println("Set ${UPDATE_PROPERTY_KEY} system property to 'true' to update entities code in the sources")
      return
    }

    executeWorkspaceCodeGeneration(::updateIntellijWorkspaceCode)
  }

  private fun executeWorkspaceCodeGeneration(
    processGenerated: (MutableEntityStorage, SourceRootEntity, Pair<VirtualFile, VirtualFile>) -> Boolean,
  ) {
    val (storage, jpsProjectSerializer) = runBlocking { loadProjectIntellijProject() }

    val modulesToCheck = findModulesWhichRequireWorkspace(storage)

    var storageChanged = false
    modulesToCheck.forEach { (moduleEntity, sourceRoot) ->
      val isTestModule = sourceRoot.rootTypeId == JAVA_TEST_ROOT_ENTITY_TYPE_ID
      val libraries = LibrariesRequiredForWorkspace.getRelatedLibraries(moduleEntity.name)
      val gen = generateWorkspaceCode(moduleEntity, sourceRoot, isTestModule, libraries)
      val thisChangesStorage = processGenerated(storage, sourceRoot, gen)
      storageChanged = storageChanged || thisChangesStorage
    }
    if (storageChanged) {
      val affectedEntitySources = modulesToCheck.map { it.first.entitySource }.toSet()
      (jpsProjectSerializer as JpsProjectSerializersImpl).saveAffectedEntities(storage, affectedEntitySources, createProjectConfigLocation())
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun generateWorkspaceCode(
    moduleEntity: ModuleEntity,
    sourceRoot: SourceRootEntity,
    isTestModule: Boolean,
    libraries: List<RelatedLibrary>,
  ): Pair<VirtualFile, VirtualFile> {
    val path = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).relativize(Path.of(sourceRoot.url.presentableUrl)).invariantSeparatorsPathString
    LOG.info("Generating workspace code for module: ${moduleEntity.name}, path $path")
    myFixture.copyDirectoryToProject(path, path)
    setupCustomIndent(moduleEntity)

    if (moduleEntity.name == "intellij.javascript.impl") {
      javascriptNodeModulesPackageExclusionFixForTests()
    }

    if (libraries.isNotEmpty())
      runWriteActionAndWait {
        ModuleRootModificationUtil.updateModel(module) { model ->
          for (library in libraries) {
            library.add(model)
          }
        }
      }

    val srcAndGen = generateCode(
      relativePathToEntitiesDirectory = path,
      processAbstractTypes = moduleEntity.withAbstractTypes,
      explicitApiEnabled = false,
      isTestModule = isTestModule
    )

    if (libraries.isNotEmpty())
      runWriteActionAndWait {
        ModuleRootModificationUtil.updateModel(module) { model ->
          for (library in libraries) {
            library.remove(model)
          }
        }
      }

    return srcAndGen
  }

  private fun updateIntellijWorkspaceCode(
    storage: MutableEntityStorage,
    sourceRoot: SourceRootEntity,
    generated: Pair<VirtualFile, VirtualFile>,
  ): Boolean {
    val (srcRoot, genRoot) = generated
    val moduleEntity = sourceRoot.contentRoot.module

    val result = runWriteActionAndWait {
      var storageChanged = false

      val genSourceRoot = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.firstOrNull { it.generated }?.sourceRoot
                          ?: newGenFolder(storage, sourceRoot).also { storageChanged = true }

      val apiRootPath = Path.of(sourceRoot.url.presentableUrl)
      val implRootPath = Path.of(genSourceRoot.url.presentableUrl)
      val virtualFileManager = VirtualFileManager.getInstance()
      val apiDir = virtualFileManager.refreshAndFindFileByNioPath(apiRootPath)!!
      val implDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(implRootPath) ?: run {
        VfsUtil.createDirectories(implRootPath.pathString)
      }
      VfsUtil.copyDirectory(this, srcRoot, apiDir, VirtualFileFilter { it != genRoot })
      VfsUtil.copyDirectory(this, genRoot, implDir, null)
      storageChanged
    }

    resetCustomIndent(moduleEntity)
    (tempDirFixture as LightTempDirTestFixtureImpl).deleteAll()
    return result
  }

  private fun compareIntellijWorkspaceCode(
    @Suppress("UNUSED_PARAMETER")
    storage: MutableEntityStorage,
    sourceRoot: SourceRootEntity,
    generated: Pair<VirtualFile, VirtualFile>,
  ): Boolean {
    val (srcRoot, genRoot) = generated
    val moduleEntity = sourceRoot.contentRoot.module

    val actualSrcPath = Path.of(sourceRoot.url.presentableUrl)
    val actualGenPath = sourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.firstOrNull { it.generated }?.let {
      Path.of(it.sourceRoot.url.presentableUrl)
    } ?: error("No generated source root for ${moduleEntity.name} ${sourceRoot.url.presentableUrl}")
    val genIsInsideSrc = actualGenPath.startsWith(actualSrcPath) && actualGenPath != actualSrcPath

    val expectedSrcDir = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_api", true)
    runWriteActionAndWait {
      val vfExpectedSrcDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedSrcDir.toPath())!!
      VfsUtil.copyDirectory(this, srcRoot, vfExpectedSrcDir, null)
    }

    if (genIsInsideSrc) {
      expectedSrcDir.assertMatches(directoryContentOf(dir = actualSrcPath), filePathFilter = { it.endsWith(".kt") })
    }
    else {
      val expectedGenDir = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_impl", true)
      FileUtil.copyDir(actualGenPath.toFile(), expectedGenDir)
      runWriteActionAndWait {
        val vfExpectedGenDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedGenDir.toPath())!!
        VfsUtil.copyDirectory(this, genRoot, vfExpectedGenDir, null)
      }
      expectedSrcDir.assertMatches(directoryContentOf(dir = actualSrcPath), filePathFilter = { it.endsWith(".kt") })
      expectedGenDir.assertMatches(directoryContentOf(dir = actualGenPath), filePathFilter = { it.endsWith(".kt") })
    }

    resetCustomIndent(moduleEntity)
    (tempDirFixture as LightTempDirTestFixtureImpl).deleteAll()
    return false
  }

  private fun newGenFolder(storage: MutableEntityStorage, sourceRoot: SourceRootEntity): SourceRootEntity {
    val genFolderVirtualFile = VfsUtil.createDirectories("${sourceRoot.contentRoot.url.presentableUrl}/${WorkspaceModelGenerator.GENERATED_FOLDER_NAME}")
    val javaSourceRoot = sourceRoot.javaSourceRoots.first()
    val updatedContentRoot = storage.modifyContentRootEntity(sourceRoot.contentRoot) {
      this.sourceRoots += SourceRootEntity(genFolderVirtualFile.toVirtualFileUrl(virtualFileManager),
                                           sourceRoot.rootTypeId, sourceRoot.entitySource) {
        javaSourceRoots = listOf(JavaSourceRootPropertiesEntity(true, javaSourceRoot.packagePrefix, javaSourceRoot.entitySource))
      }
    }
    val result = updatedContentRoot.sourceRoots.last()
    return result
  }

  private suspend fun loadProjectIntellijProject(): Pair<MutableEntityStorage, JpsProjectSerializers> {
    val mutableEntityStorage = MutableEntityStorage.create()
    val configLocation = createProjectConfigLocation()
    val context = SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation))
    val jpsProjectSerializer = JpsProjectEntitiesLoader.loadProject(
      configLocation = configLocation,
      builder = mutableEntityStorage,
      orphanage = mutableEntityStorage,
      externalStoragePath = Paths.get("/tmp"),
      errorReporter = TestErrorReporter,
      context = context
    )
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

  internal object TestErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw AssertionFailedError("Failed to load ${file.url}: $message")
    }
  }

  private fun javascriptNodeModulesPackageExclusionFixForTests() {
    runWriteActionAndWait {
      myFixture.project.workspaceModel.updateProjectModel("remove node_modules exclusion in test") {
        it.replaceBySource({ it !is JpsFileEntitySource }, MutableEntityStorage.create())
      }
    }
  }

  companion object {
    private const val UPDATE_PROPERTY_KEY = "intellij.workspace.model.update.entities"

    private fun mergePatterns(vararg patterns: String): String {
      return patterns.joinToString("|") { "($it)" }
    }

    // TODO :: Fix detection of entities in modules
    private val regexToDetectWsmClasses = Regex(mergePatterns(
      // Regex for searching entities that implements `ModuleSettingsFacetBridgeEntity`
      "interface [a-zA-Z0-9]+\\s*:\\s*ModuleSettingsFacetBridgeEntity[a-zA-Z0-9]*",
      // Regex for searching regular entities in modules
      "interface [a-zA-Z0-9]+\\s*:\\s*WorkspaceEntity[a-zA-Z0-9]*",
      // Regex for searching entity source implementations in modules
      "(class|object) [a-zA-Z0-9]+\\s*(\\(.*\\))?\\s*:\\s*EntitySource",
      // Regex for searching symbolic id implementations in modules
      "(class|object) [a-zA-Z0-9]+\\s*(\\(.*\\))?\\s*:\\s*SymbolicEntityId<[a-zA-Z0-9]*>"
    ))

    private val ModuleEntity.withAbstractTypes: Boolean
      get() = name in modulesWithAbstractTypes || name.startsWith(RIDER_MODULES_PREFIX)

    private val modulesWithCustomIndentSize: Map<String, Int> = mapOf("kotlin.base.scripting" to 4)

    private val skippedModules: Set<String> = setOf(
      "intellij.platform.workspace.storage.tests",
      "intellij.platform.workspace.storage.testEntities",
      "intellij.java.compiler.tests" // IJPL-178663
    )

    private fun processFileIfMatch(file: File, regex: Regex, function: (File) -> Unit) {
      if (regex.containsMatchIn(file.readText())) {
        function.invoke(file)
      }
    }

    private fun findModulesWhichRequireWorkspace(storage: EntityStorage): MutableSet<Pair<ModuleEntity, SourceRootEntity>> {
      val modulesToCheck = mutableSetOf<Pair<ModuleEntity, SourceRootEntity>>()

      srcRoots@ for (sourceRoot in storage.entities<SourceRootEntity>()) {
        val moduleEntity = sourceRoot.contentRoot.module
        var toCheck = false

        if (moduleEntity.name in skippedModules) continue

        for (file in File(sourceRoot.url.presentableUrl).walk()) {
          if (file.isFile && file.extension == "kt") {
            processFileIfMatch(file, regexToDetectWsmClasses) {
              toCheck = true
              modulesToCheck.add(moduleEntity to sourceRoot)
            }
          }
          if (toCheck) continue@srcRoots
        }
      }
      return modulesToCheck
    }
  }
}