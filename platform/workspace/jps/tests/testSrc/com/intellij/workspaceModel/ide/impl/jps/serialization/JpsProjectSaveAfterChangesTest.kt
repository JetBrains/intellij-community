package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.java.workspace.entities.modifyEntity
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.workspace.jps.JpsEntitySourceFactory
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class JpsProjectSaveAfterChangesTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = IdeVirtualFileUrlManagerImpl()
  }

  @ParameterizedTest(name = "unloaded = {0}")
  @ValueSource(strings = ["", "util", "util,main", "main"])
  fun `modify module`(unloaded: String) {
    val unloadedHolder = unloadedHolder(unloaded)
    checkSaveProjectAfterChange("common/modifyIml", "common/modifyIml",
                                unloadedHolder) { mainBuilder, _, unloadedEntitiesBuilder, configLocation ->
      val builder = if (unloadedHolder.isUnloaded("util")) unloadedEntitiesBuilder else mainBuilder
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      val sourceRoot = utilModule.sourceRoots.first()
      builder.modifyEntity(sourceRoot) {
        url = configLocation.baseDirectoryUrl.append("util/src2")
      }
      builder.modifyEntity(utilModule.customImlData!!) {
        rootManagerTagCustomData = """<component>
  <annotation-paths>
    <root url="${configLocation.baseDirectoryUrlString}/lib/anno2" />
  </annotation-paths>
  <javadoc-paths>
    <root url="${configLocation.baseDirectoryUrlString}/lib/javadoc2" />
  </javadoc-paths>
</component>"""
      }
      builder.modifyEntity(utilModule) {
        dependencies.removeLast()
        dependencies.removeLast()
      }
      builder.modifyEntity(utilModule.contentRoots.first()) {
        excludedPatterns = mutableListOf()
        excludedUrls = mutableListOf()
      }
      builder.modifyEntity(sourceRoot.asJavaSourceRoot()!!) {
        packagePrefix = ""
      }
    }
  }

  @Test
  fun `rename module`() {
    checkSaveProjectAfterChange("directoryBased/renameModule", "fileBased/renameModule") { builder, _, _, _ ->
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      builder.modifyEntity(utilModule) {
        name = "util2"
      }
    }
  }


  @Test
  fun `add library and check vfu index not empty`() {
    checkSaveProjectAfterChange("directoryBased/addLibrary", "fileBased/addLibrary") { builder, _, _, configLocation ->
      val root = LibraryRoot(
        virtualFileManager.getOrCreateFromUri("jar://${JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString)}/lib/junit2.jar!/"),
        LibraryRootTypeId.COMPILED)
      val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(configLocation)
      builder addEntity LibraryEntity("junit2", LibraryTableId.ProjectLibraryTableId, listOf(root), source)
      builder.entities(LibraryEntity::class.java).forEach { libraryEntity ->
        val virtualFileUrl = libraryEntity.roots.first().url
        val entitiesByUrl = builder.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl)
        assertTrue(entitiesByUrl.toList().isNotEmpty())
      }
    }
  }

  @ParameterizedTest(name = "unloaded = {0}")
  @ValueSource(strings = ["", "newModule", "newModule,main", "main"])
  fun `add module`(unloaded: String) {
    val unloadedHolder = unloadedHolder(unloaded);
    checkSaveProjectAfterChange("directoryBased/addModule", "fileBased/addModule",
                                unloadedHolder) { mainBuilder, _, unloadedEntitiesBuilder, configLocation ->
      val builder = if (unloadedHolder.isUnloaded("newModule")) unloadedEntitiesBuilder else mainBuilder
      val source = JpsProjectFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
      val dependencies = listOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
      val module = builder addEntity ModuleEntity("newModule", dependencies, source)
      builder.modifyEntity(module) {
        type = "JAVA_MODULE"
      }
      val contentRootEntity = builder addEntity ContentRootEntity(configLocation.baseDirectoryUrl.append("new"),
                                                                  emptyList<@NlsSafe String>(), module.entitySource) {
        this@ContentRootEntity.module = module
      }
      val sourceRootEntity = builder addEntity SourceRootEntity(configLocation.baseDirectoryUrl.append("new"),
                                                                "java-source", source) {
        contentRoot = contentRootEntity
      }
      builder addEntity JavaSourceRootPropertiesEntity(false, "", sourceRootEntity.entitySource) {
        sourceRoot = sourceRootEntity
      }
      builder addEntity JavaModuleSettingsEntity(true, true, source) {
        this.module = module
      }
    }
  }

  @ParameterizedTest(name = "unloaded = {0}")
  @ValueSource(strings = ["", "util", "util,main", "main"])
  fun `remove module`(unloaded: String) {
    val unloadedHolder = unloadedHolder(unloaded)
    checkSaveProjectAfterChange("directoryBased/removeModule", "fileBased/removeModule",
                                unloadedHolder) { mainBuilder, _, unloadedEntitiesBuilder, _ ->
      val builder = if (unloadedHolder.isUnloaded("util")) unloadedEntitiesBuilder else mainBuilder
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      //todo now we need to remove module libraries by hand, maybe we should somehow modify the model instead
      val moduleLibraries = utilModule.getModuleLibraries(builder).toList()
      builder.removeEntity(utilModule)
      moduleLibraries.forEach {
        builder.removeEntity(it)
      }
    }
  }

  @Test
  fun `modify library`() {
    checkSaveProjectAfterChange("directoryBased/modifyLibrary", "fileBased/modifyLibrary") { builder, _, _, configLocation ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      val root = LibraryRoot(
        virtualFileManager.getOrCreateFromUri("jar://${JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString)}/lib/junit2.jar!/"),
        LibraryRootTypeId.COMPILED)
      builder.modifyEntity(junitLibrary) {
        roots = mutableListOf(root)
      }
    }
  }

  @Test
  fun `rename library`() {
    checkSaveProjectAfterChange("directoryBased/renameLibrary", "fileBased/renameLibrary") { builder, _, _, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      builder.modifyEntity(junitLibrary) {
        name = "junit2"
      }
    }
  }

  @Test
  fun `remove library`() {
    checkSaveProjectAfterChange("directoryBased/removeLibrary", "fileBased/removeLibrary") { builder, _, _, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      builder.removeEntity(junitLibrary)
    }
  }

  @Test
  fun `set group for the module`() {
    checkSaveProjectAfterChange("directoryBased/addModuleGroup", "fileBased/addModuleGroup") { builder, _, _, _ ->
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      builder addEntity ModuleGroupPathEntity(listOf("group"), utilModule.entitySource) {
        this.module = utilModule
      }
    }
  }

  private fun unloadedHolder(unloaded: String): com.intellij.platform.workspace.jps.UnloadedModulesNameHolder {
    val unloadedModuleNames = StringUtil.split(unloaded, ",").toSet()
    return object : com.intellij.platform.workspace.jps.UnloadedModulesNameHolder {
      override fun isUnloaded(name: String?) = name in unloadedModuleNames

      override fun hasUnloaded() = !unloadedModuleNames.isEmpty()

    }
  }

  private fun checkSaveProjectAfterChange(directoryNameForDirectoryBased: String,
                                          directoryNameForFileBased: String,
                                          unloadedModuleNameHolder: com.intellij.platform.workspace.jps.UnloadedModulesNameHolder = com.intellij.platform.workspace.jps.UnloadedModulesNameHolder.DUMMY,
                                          change: (MutableEntityStorage, MutableEntityStorage, MutableEntityStorage, JpsProjectConfigLocation) -> Unit) {
    checkSaveProjectAfterChange(sampleDirBasedProjectFile, directoryNameForDirectoryBased, change, unloadedModuleNameHolder,
                                virtualFileManager, "serialization/reload")
    checkSaveProjectAfterChange(sampleFileBasedProjectFile, directoryNameForFileBased, change, unloadedModuleNameHolder, virtualFileManager,
                                "serialization/reload")
  }
}