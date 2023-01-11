package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.*

class JpsProjectSaveAfterChangesTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = IdeVirtualFileUrlManagerImpl()
  }

  @Test
  fun `modify module`() {
    checkSaveProjectAfterChange("common/modifyIml", "common/modifyIml") { builder, configLocation ->
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
    checkSaveProjectAfterChange("directoryBased/renameModule", "fileBased/renameModule") { builder, _ ->
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      builder.modifyEntity(utilModule) {
        name = "util2"
      }
    }
  }


  @Test
  fun `add library and check vfu index not empty`() {
    checkSaveProjectAfterChange("directoryBased/addLibrary", "fileBased/addLibrary") { builder, configLocation ->
      val root = LibraryRoot(virtualFileManager.fromUrl("jar://${JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString)}/lib/junit2.jar!/"), LibraryRootTypeId.COMPILED)
      val source = JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(configLocation)
      builder.addLibraryEntity("junit2", LibraryTableId.ProjectLibraryTableId, listOf(root), emptyList(), source)
      builder.entities(LibraryEntity::class.java).forEach { libraryEntity ->
        val virtualFileUrl = libraryEntity.roots.first().url
        val entitiesByUrl = builder.getMutableVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl)
        Assert.assertTrue(entitiesByUrl.toList().isNotEmpty())
      }
    }
  }

  @Test
  fun `add module`() {
    checkSaveProjectAfterChange("directoryBased/addModule", "fileBased/addModule") { builder, configLocation ->
      val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
      val dependencies = listOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
      val module = builder.addModuleEntity("newModule", dependencies, source)
      builder.modifyEntity(module) {
        type = "JAVA_MODULE"
      }
      val contentRootEntity = builder.addContentRootEntity(configLocation.baseDirectoryUrl.append("new"), emptyList(),
                                                           emptyList(), module)
      val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity, configLocation.baseDirectoryUrl.append("new"),
                                                         "java-source", source)
      builder.addJavaSourceRootEntity(sourceRootEntity, false, "")
      builder.addJavaModuleSettingsEntity(true, true, null, null, null, module, source)
    }
  }

  @Test
  fun `remove module`() {
    checkSaveProjectAfterChange("directoryBased/removeModule", "fileBased/removeModule") { builder, _ ->
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
    checkSaveProjectAfterChange("directoryBased/modifyLibrary", "fileBased/modifyLibrary") { builder, configLocation ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      val root = LibraryRoot(virtualFileManager.fromUrl("jar://${JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString)}/lib/junit2.jar!/"),
                             LibraryRootTypeId.COMPILED)
      builder.modifyEntity(junitLibrary) {
        roots = mutableListOf(root)
      }
    }
  }

  @Test
  fun `rename library`() {
    checkSaveProjectAfterChange("directoryBased/renameLibrary", "fileBased/renameLibrary") { builder, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      builder.modifyEntity(junitLibrary) {
        name = "junit2"
      }
    }
  }

  @Test
  fun `remove library`() {
    checkSaveProjectAfterChange("directoryBased/removeLibrary", "fileBased/removeLibrary") { builder, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      builder.removeEntity(junitLibrary)
    }
  }

  private fun checkSaveProjectAfterChange(directoryNameForDirectoryBased: String,
                                          directoryNameForFileBased: String,
                                          change: (MutableEntityStorage, JpsProjectConfigLocation) -> Unit) {
    checkSaveProjectAfterChange(sampleDirBasedProjectFile, directoryNameForDirectoryBased, change, virtualFileManager, "serialization/reload")
    checkSaveProjectAfterChange(sampleFileBasedProjectFile, directoryNameForFileBased, change, virtualFileManager, "serialization/reload")
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}