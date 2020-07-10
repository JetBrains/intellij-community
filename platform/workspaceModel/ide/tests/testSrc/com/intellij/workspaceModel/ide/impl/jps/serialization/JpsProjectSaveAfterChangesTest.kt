package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.storage.impl.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.append
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class JpsProjectSaveAfterChangesTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `modify module`() {
    checkSaveProjectAfterChange("common/modifyIml", "common/modifyIml") { builder, configLocation ->
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      val sourceRoot = utilModule.sourceRoots.first()
      builder.modifyEntity(ModifiableSourceRootEntity::class.java, sourceRoot) {
        url = configLocation.baseDirectoryUrl.append("util/src2")
      }
      builder.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, utilModule.customImlData!!) {
        rootManagerTagCustomData = """<component LANGUAGE_LEVEL="JDK_1_7">
  <annotation-paths>
    <root url="${configLocation.baseDirectoryUrlString}/lib/anno2" />
  </annotation-paths>
  <javadoc-paths>
    <root url="${configLocation.baseDirectoryUrlString}/lib/javadoc2" />
  </javadoc-paths>
</component>"""
      }
      builder.modifyEntity(ModifiableModuleEntity::class.java, utilModule) {
        dependencies = dependencies.dropLast(2)
      }
      builder.modifyEntity(ModifiableContentRootEntity::class.java, utilModule.contentRoots.first()) {
        excludedPatterns = emptyList()
        excludedUrls = emptyList()
      }
      builder.modifyEntity(ModifiableJavaSourceRootEntity::class.java, sourceRoot.asJavaSourceRoot()!!) {
        packagePrefix = ""
      }
    }
  }

  @Test
  fun `rename module`() {
    checkSaveProjectAfterChange("directoryBased/renameModule", "fileBased/renameModule") { builder, _ ->
      val utilModule = builder.entities(ModuleEntity::class.java).first { it.name == "util" }
      builder.modifyEntity(ModifiableModuleEntity::class.java, utilModule) {
        name = "util2"
      }
    }
  }


  @Test
  fun `add library`() {
    checkSaveProjectAfterChange("directoryBased/addLibrary", "fileBased/addLibrary") { builder, configLocation ->
      val root = LibraryRoot(virtualFileManager.fromUrl("jar://${JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString)}/lib/junit2.jar!/"),
                             LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)
      val source = JpsProjectEntitiesLoader.createJpsEntitySourceForProjectLibrary(configLocation)
      builder.addLibraryEntity("junit2", LibraryTableId.ProjectLibraryTableId, listOf(root), emptyList(), source)
    }
  }

  @Test
  fun `add module`() {
    checkSaveProjectAfterChange("directoryBased/addModule", "fileBased/addModule") { builder, configLocation ->
      val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)
      val dependencies = listOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
      val module = builder.addModuleEntity("newModule", dependencies, source)
      builder.modifyEntity(ModifiableModuleEntity::class.java, module) {
        type = "JAVA_MODULE"
      }
      val contentRootEntity = builder.addContentRootEntity(configLocation.baseDirectoryUrl.append("new"), emptyList(), emptyList(),
                                                           module, source)
      val sourceRootEntity = builder.addSourceRootEntity(module, contentRootEntity, configLocation.baseDirectoryUrl.append("new"), false,
                                                         "java-source", source)
      builder.addJavaSourceRootEntity(sourceRootEntity, false, "", source)
      builder.addJavaModuleSettingsEntity(true, true, null, null, module, source)
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
                             LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)
      builder.modifyEntity(ModifiableLibraryEntity::class.java, junitLibrary) {
        roots = listOf(root)
      }
    }
  }

  @Test
  fun `rename library`() {
    checkSaveProjectAfterChange("directoryBased/renameLibrary", "fileBased/renameLibrary") { builder, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class.java).first { it.name == "junit" }
      builder.modifyEntity(ModifiableLibraryEntity::class.java, junitLibrary) {
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
                                          change: (WorkspaceEntityStorageBuilder, JpsProjectConfigLocation) -> Unit) {
    checkSaveProjectAfterChange(sampleDirBasedProjectFile, directoryNameForDirectoryBased, change)
    checkSaveProjectAfterChange(sampleFileBasedProjectFile, directoryNameForFileBased, change)
  }

  private fun checkSaveProjectAfterChange(originalProjectFile: File, changedFilesDirectoryName: String?,
                                          change: (WorkspaceEntityStorageBuilder, JpsProjectConfigLocation) -> Unit) {
    val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager)
    val builder = WorkspaceEntityStorageBuilder.from(projectData.storage)
    change(builder, projectData.configLocation)
    val changesMap = builder.collectChanges(projectData.storage)
    val changedSources = changesMap.values.flatMapTo(HashSet()) { changes -> changes.flatMap { change ->
      when (change) {
        is EntityChange.Added -> listOf(change.entity)
        is EntityChange.Removed -> listOf(change.entity)
        is EntityChange.Replaced -> listOf(change.oldEntity, change.newEntity)
      }
    }.map { it.entitySource }}
    val writer = JpsFileContentWriterImpl()
    projectData.serializers.saveEntities(builder.toStorage(), changedSources, writer)
    writer.writeFiles(projectData.projectDir)
    projectData.serializers.checkConsistency(projectData.projectDirUrl, builder.toStorage(), virtualFileManager)

    val expectedDir = FileUtil.createTempDirectory("jpsProjectTest", "expected")
    FileUtil.copyDir(projectData.originalProjectDir, expectedDir)
    if (changedFilesDirectoryName != null) {
      val changedDir = PathManagerEx.findFileUnderCommunityHome("platform/workspaceModel/ide/tests/testData/serialization/reload/$changedFilesDirectoryName")
      FileUtil.copyDir(changedDir, expectedDir)
    }
    expectedDir.walk().filter { it.isFile && it.readText().trim() == "<delete/>" }.forEach {
      FileUtil.delete(it)
    }

    assertDirectoryMatches(projectData.projectDir, expectedDir,
                           emptySet(),
                           emptyList())
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}