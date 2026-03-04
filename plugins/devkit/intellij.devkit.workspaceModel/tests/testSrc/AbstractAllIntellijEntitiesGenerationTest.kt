// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator.Companion.RIDER_MODULES_PREFIX
import com.intellij.devkit.workspaceModel.WorkspaceModelGenerator.Companion.modulesWithAbstractTypes
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
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
import com.intellij.pom.PomManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testNameFixture
import com.intellij.util.SystemProperties
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.CachingJpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.SerializationContextForTests
import com.intellij.workspaceModel.ide.impl.jps.serialization.saveAffectedEntities
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import junit.framework.AssertionFailedError
import kotlinx.coroutines.runBlocking
import org.editorconfig.Utils
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractAllIntellijEntitiesGenerationTest {
  private val virtualFileManager = IdeVirtualFileUrlManagerImpl()

  private val tempPath = tempPathFixture(prefix = this.javaClass.simpleName)
  private val projectRoot: VirtualFile
    get() = VfsUtil.findFile(tempPath.get(), true)!!
  private val projectFixture =
    projectFixture(pathFixture = tempPath, openProjectTask = OpenProjectTask { createModule = false }, openAfterCreation = true)
  private val project: Project
    get() = projectFixture.get()
  private val testName = testNameFixture()
  private val disposable = disposableFixture()

  val actualSrcRoot: VirtualFile
    get() = VfsUtil.findFile(tempPath.get().resolve("src").findOrCreateDirectory(), true)!!

  val actualGenRoot: VirtualFile
    get() = VfsUtil.findFile(tempPath.get().resolve("gen").findOrCreateDirectory(), true)!!

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(true)
    Utils.isEnabledInTests = true

    CodeGeneratorVersions.checkApiInInterface = false
    CodeGeneratorVersions.checkApiInImpl = false
    CodeGeneratorVersions.checkImplInImpl = false

    //enableKotlinOfficialCodeStyle(project)
    val jdk = IdeaTestUtil.getMockJdk21()
    writeAction {
      ProjectJdkTable.getInstance().addJdk(jdk, disposable.get())
    }

    val wsm = project.workspaceModel
    //@formatter:off
    wsm.update("Setup project roots") {
      val module = ModuleEntity("${this@AbstractAllIntellijEntitiesGenerationTest.javaClass.simpleName}_${testName}_module", listOf(ModuleSourceDependency), NonPersistentEntitySource)
      val contentRoot = ContentRootEntity(projectRoot.toVirtualFileUrl(wsm.getVirtualFileUrlManager()), emptyList(), NonPersistentEntitySource)
      module.contentRoots += contentRoot
      contentRoot.sourceRoots += SourceRootEntity(actualSrcRoot.toVirtualFileUrl(wsm.getVirtualFileUrlManager()), JAVA_SOURCE_ROOT_ENTITY_TYPE_ID, NonPersistentEntitySource)
      val genSourceRoot = SourceRootEntity(actualGenRoot.toVirtualFileUrl(wsm.getVirtualFileUrlManager()), JAVA_SOURCE_ROOT_ENTITY_TYPE_ID, NonPersistentEntitySource)
      genSourceRoot.javaSourceRoots += JavaSourceRootPropertiesEntity(generated = true, packagePrefix = "", entitySource = NonPersistentEntitySource)
      contentRoot.sourceRoots += genSourceRoot

      it.addEntity(module)
    }
    //@formatter:on

    writeAction {
      val ultimateEditorconfig = VfsUtil.findFile(Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).resolve(".editorconfig"), true)
      VfsUtil.copyFile(this@AbstractAllIntellijEntitiesGenerationTest, ultimateEditorconfig!!, projectRoot)
    }

    val module = project.modules.first()
    val model = readAction { ModuleRootManager.getInstance(module).getModifiableModel() }

    LibrariesRequiredForWorkspace.workspaceStorage.add(model)
    LibrariesRequiredForWorkspace.workspaceJpsEntities.add(model)

    writeAction {
      model.sdk = jdk
      model.commit()
    }

    suspendUntilIndexesAreReady(project)

    // Load codegen jar on warm-up phase
    CodegenJarLoader.getInstance(project).getClassLoader()
    PomManager.getModel(project) // initialize PostprocessReformattingAspectImpl to enable reformatting after PSI changes
  }

  @AfterEach
  fun tearDown(): Unit = runBlocking {
    CodeGeneratorVersions.checkApiInInterface = true
    CodeGeneratorVersions.checkApiInImpl = true
    CodeGeneratorVersions.checkImplInImpl = true
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("modules")
  open fun `test generation of all entities in intellij codebase`(
    moduleName: String,
    ultimateModuleEntity: ModuleEntity,
    ultimateSourceRoot: SourceRootEntity,
    ultimateStorage: MutableEntityStorage,
    jpsProjectSerializer: JpsProjectSerializers,
  ) {
    executeWorkspaceCodeGeneration(ultimateModuleEntity,
                                   ultimateSourceRoot,
                                   ultimateStorage,
                                   jpsProjectSerializer,
                                   ::compareIntellijWorkspaceCode)
  }

  //@IJIgnore(issue = "IDEA-364751")
  @ParameterizedTest(name = "{0}")
  @MethodSource("modules")
  fun `test update code`(
    moduleName: String,
    ultimateModuleEntity: ModuleEntity,
    ultimateSourceRoot: SourceRootEntity,
    ultimateStorage: MutableEntityStorage,
    jpsProjectSerializer: JpsProjectSerializers,
  ) {
    if (!SystemProperties.getBooleanProperty(UPDATE_PROPERTY_KEY, false)) {
      println("Set ${UPDATE_PROPERTY_KEY} system property to 'true' to update entities code in the sources")
      return
    }

    executeWorkspaceCodeGeneration(ultimateModuleEntity,
                                   ultimateSourceRoot,
                                   ultimateStorage,
                                   jpsProjectSerializer,
                                   ::updateIntellijWorkspaceCode)
  }

  private fun executeWorkspaceCodeGeneration(
    ultimateModuleEntity: ModuleEntity,
    ultimateSourceRoot: SourceRootEntity,
    ultimateStorage: MutableEntityStorage,
    jpsProjectSerializer: JpsProjectSerializers,
    processGenerated: suspend (MutableEntityStorage, SourceRootEntity, VirtualFile, VirtualFile) -> Boolean,
  ): Unit = runBlocking {
    println("Generating workspace code for module ${ultimateModuleEntity.name} [${ultimateSourceRoot.url.presentableUrl}]")
    val ultimateSourceRootPath =
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Path.of(ultimateSourceRoot.url.presentableUrl))!!

    writeAction {
      VfsUtil.copyDirectory(this, ultimateSourceRootPath, actualSrcRoot, null)
    }

    val isTestModule = ultimateSourceRoot.rootTypeId == JAVA_TEST_ROOT_ENTITY_TYPE_ID
    val libraries = LibrariesRequiredForWorkspace.getRelatedLibraries(ultimateModuleEntity.name)
    if (ultimateModuleEntity.name == "intellij.javascript.backend") {
      javascriptNodeModulesPackageExclusionFixForTests()
    }

    val testProjectModule = project.modules[0]
    if (libraries.isNotEmpty()) {
      writeAction {
        ModuleRootModificationUtil.updateModel(testProjectModule) { model ->
          for (library in libraries) {
            library.add(model)
          }
        }
      }
    }

    suspendUntilIndexesAreReady(project)

    CodeWriter.generate(project = project,
                        module = testProjectModule,
                        actualSrcRoot,
                        processAbstractTypes = ultimateModuleEntity.withAbstractTypes,
                        explicitApiEnabled = false,
                        isTestSourceFolder = false,
                        isTestModule = isTestModule,
                        targetFolderGenerator = { actualGenRoot },
                        existingTargetFolder = { actualGenRoot },
                        formatCode = true)
    FileDocumentManager.getInstance().saveAllDocuments()

    val thisChangesStorage = processGenerated(ultimateStorage, ultimateSourceRoot, actualSrcRoot, actualGenRoot)
    if (thisChangesStorage) {
      (jpsProjectSerializer as JpsProjectSerializersImpl).saveAffectedEntities(ultimateStorage,
                                                                               setOf(ultimateModuleEntity.entitySource),
                                                                               createProjectConfigLocation())
    }
  }

  private suspend fun updateIntellijWorkspaceCode(
    ultimateStorage: MutableEntityStorage,
    ultimateSourceRoot: SourceRootEntity,
    newSrcRoot: VirtualFile,
    newGenRoot: VirtualFile,
  ): Boolean {
    return writeAction {
      var storageChanged = false

      val ultimateGenSourceRoot = findGenSourceRoot(ultimateSourceRoot)
                                  ?: createGenSourceRoot(ultimateStorage, ultimateSourceRoot).also { storageChanged = true }

      val ultimateGenSourceRootVirtualFile = ultimateGenSourceRoot.url.toVirtualFile() ?: run {
        VfsUtil.createDirectories(ultimateGenSourceRoot.url.presentableUrl)
      }
      VfsUtil.copyDirectory(this, newSrcRoot, ultimateSourceRoot.url.toVirtualFile()!!, null)
      VfsUtil.copyDirectory(this, newGenRoot, ultimateGenSourceRootVirtualFile, null)
      storageChanged
    }
  }

  private suspend fun compareIntellijWorkspaceCode(
    @Suppress("UNUSED_PARAMETER")
    storage: MutableEntityStorage,
    ultimateSourceRoot: SourceRootEntity,
    newSrcRoot: VirtualFile,
    newGenRoot: VirtualFile,
  ): Boolean {
    val moduleEntity = ultimateSourceRoot.contentRoot.module

    val ultimateSrcPath = Path.of(ultimateSourceRoot.url.presentableUrl)
    val ultimateGenPath = ultimateSourceRoot.contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.firstOrNull { it.generated }?.let {
      Path.of(it.sourceRoot.url.presentableUrl)
    } ?: error("No generated source root for ${moduleEntity.name} ${ultimateSourceRoot.url.presentableUrl}")
    val genIsInsideSrc = ultimateGenPath.startsWith(ultimateSrcPath) && ultimateGenPath != ultimateSrcPath

    //val expectedSrcDir = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_api", true)
    //runWriteActionAndWait {
    //  val vfExpectedSrcDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedSrcDir.toPath())!!
    //  VfsUtil.copyDirectory(this, newSrcRoot, vfExpectedSrcDir, null)
    //}

    if (genIsInsideSrc) {
      Path.of(newSrcRoot.presentableUrl).assertMatches(directoryContentOf(dir = ultimateSrcPath), filePathFilter = { it.endsWith(".kt") }, ignoreEmptyDirectories = true)
    }
    else {
      //val expectedGenDir = FileUtil.createTempDirectory(CodeGenerationTestBase::class.java.simpleName, "${testDirectoryName}_impl", true)
      //FileUtil.copyDir(ultimateGenPath.toFile(), expectedGenDir)
      //runWriteActionAndWait {
      //  val vfExpectedGenDir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(expectedGenDir.toPath())!!
      //  VfsUtil.copyDirectory(this, newGenRoot, vfExpectedGenDir, null)
      //}
      Path.of(newSrcRoot.presentableUrl).assertMatches(directoryContentOf(dir = ultimateSrcPath), filePathFilter = { it.endsWith(".kt") }, ignoreEmptyDirectories = true)
      Path.of(newGenRoot.presentableUrl).assertMatches(directoryContentOf(dir = ultimateGenPath), filePathFilter = { it.endsWith(".kt") }, ignoreEmptyDirectories = true)
    }

    return false
  }

  private fun findGenSourceRoot(sourceRoot: SourceRootEntity): SourceRootEntity? {
    val closest = sourceRoot.javaSourceRoots.firstOrNull { it.generated }
    if (closest != null) {
      return closest.sourceRoot
    }
    val contentRoot = sourceRoot.contentRoot
    val inContentRoot = contentRoot.sourceRoots.flatMap { it.javaSourceRoots }.firstOrNull { it.generated }
    if (inContentRoot != null) {
      return inContentRoot.sourceRoot
    }
    return null
  }

  private fun createGenSourceRoot(storage: MutableEntityStorage, sourceRoot: SourceRootEntity): SourceRootEntity {
    val genFolderVirtualFile =
      VfsUtil.createDirectories("${sourceRoot.contentRoot.url.presentableUrl}/${WorkspaceModelGenerator.GENERATED_FOLDER_NAME}")
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

  internal object TestErrorReporter : ErrorReporter {
    override fun reportError(message: String, file: VirtualFileUrl) {
      throw AssertionFailedError("Failed to load ${file.url}: $message")
    }
  }

  private fun javascriptNodeModulesPackageExclusionFixForTests() {
    runWriteActionAndWait {
      project.workspaceModel.updateProjectModel("remove node_modules exclusion in test") {
        it.replaceBySource({ it !is JpsFileEntitySource }, MutableEntityStorage.create())
      }
    }
  }

  companion object {
    private const val UPDATE_PROPERTY_KEY = "intellij.workspace.model.update.entities"

    @JvmStatic
    fun modules(): List<Arguments> = runBlocking {
      val (storage, jpsProjectSerializer) = loadProjectIntellijProject()

      val modulesToCheck = findModulesWhichRequireWorkspace(storage)
      modulesToCheck.map { (module, source) ->
        Arguments.of(module.name, module, source, storage, jpsProjectSerializer)
      }
    }

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

    private val skippedModules: Set<String> = setOf(
      "intellij.platform.workspace.storage.tests",
      "intellij.platform.workspace.storage.testEntities",
      "intellij.java.compiler.tests", // IJPL-178663
      "intellij.graphql",
      "intellij.gradle.tests",
      "intellij.platform.lang.tests",
      "intellij.java.impl" // IJPL-196541
    )

    private fun processFileIfMatch(file: File, regex: Regex, function: (File) -> Unit) {
      if (regex.containsMatchIn(file.readText())) {
        function.invoke(file)
      }
    }

    private fun findModulesWhichRequireWorkspace(storage: EntityStorage): List<Pair<ModuleEntity, SourceRootEntity>> {
      val modulesToCheck = mutableListOf<Pair<ModuleEntity, SourceRootEntity>>()

      srcRoots@ for (sourceRoot in storage.entities<SourceRootEntity>()) {
        val moduleEntity = sourceRoot.contentRoot.module

        if (moduleEntity.name in skippedModules) continue
        if (sourceRoot.javaSourceRoots.none { !it.generated }) continue

        var toCheck = false
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

private suspend fun loadProjectIntellijProject(): Pair<MutableEntityStorage, JpsProjectSerializers> {
  val virtualFileManager = IdeVirtualFileUrlManagerImpl()
  val mutableEntityStorage = MutableEntityStorage.create()
  val configLocation = createProjectConfigLocation()
  val context = SerializationContextForTests(virtualFileManager, CachingJpsFileContentReader(configLocation))
  val jpsProjectSerializer = JpsProjectEntitiesLoader.loadProject(
    configLocation = configLocation,
    builder = mutableEntityStorage,
    orphanage = mutableEntityStorage,
    externalStoragePath = Paths.get("/tmp"),
    errorReporter = AbstractAllIntellijEntitiesGenerationTest.TestErrorReporter,
    context = context
  )
  return mutableEntityStorage to jpsProjectSerializer
}

private fun createProjectConfigLocation(): JpsProjectConfigLocation {
  val virtualFileManager = IdeVirtualFileUrlManagerImpl()
  val projectDir = Path.of(IdeaTestExecutionPolicy.getHomePathWithPolicy()).toVirtualFileUrl(virtualFileManager)
  return JpsProjectConfigLocation.DirectoryBased(projectDir, projectDir.append(PathMacroUtil.DIRECTORY_STORE_NAME))
}

private fun VirtualFileUrl.toVirtualFile(): VirtualFile? {
  return VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Path.of(presentableUrl))
}