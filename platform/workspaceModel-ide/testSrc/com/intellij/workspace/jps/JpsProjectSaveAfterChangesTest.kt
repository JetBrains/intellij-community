package com.intellij.workspace.jps

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.IdeUiEntitySource
import com.intellij.workspace.ide.JpsFileEntitySource
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class JpsProjectSaveAfterChangesTest {
  @Test
  fun `modify module`() {
    checkSaveProjectAfterChange("common/modifyIml", "common/modifyIml") { builder, projectDirUrl ->
      val utilModule = builder.entities(ModuleEntity::class).first { it.name == "util" }
      val sourceRoot = utilModule.sourceRoots.first()
      builder.modifyEntity(ModifiableSourceRootEntity::class.java, sourceRoot) {
        url = VirtualFileUrlManager.fromUrl("$projectDirUrl/util/src2")
      }
      builder.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, utilModule.customImlData!!) {
        rootManagerTagCustomData = """<component LANGUAGE_LEVEL="JDK_1_7">
  <annotation-paths>
    <root url="$projectDirUrl/lib/anno2" />
  </annotation-paths>
  <javadoc-paths>
    <root url="$projectDirUrl/lib/javadoc2" />
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
  fun `add library`() {
    checkSaveProjectAfterChange("directoryBased/addLibrary", "fileBased/addLibrary") { builder, projectDirUrl ->
      val root = LibraryRoot(VirtualFileUrlManager.fromUrl("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/"),
                             LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)
      builder.addLibraryEntity("junit2", LibraryTableId.ProjectLibraryTableId, listOf(root), emptyList(), IdeUiEntitySource)
    }
  }

  @Test
  fun `add module`() {
    checkSaveProjectAfterChange("directoryBased/addModule", "fileBased/addModule") { builder, projectDirUrl ->
      val projectPlace = (builder.entities(ModuleEntity::class).first().entitySource as JpsFileEntitySource).projectPlace
      val source = JpsFileEntitySource(VirtualFileUrlManager.fromUrl("$projectDirUrl/newModule.iml"), projectPlace)
      val dependencies = listOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
      val module = builder.addModuleEntity("newModule", dependencies, source)
      builder.addContentRootEntity(VirtualFileUrlManager.fromUrl("$projectDirUrl/new"), emptyList(), emptyList(), module, source)
      val sourceRootEntity = builder.addSourceRootEntity(module, VirtualFileUrlManager.fromUrl("$projectDirUrl/new"), false, "java-source", source)
      builder.addJavaSourceRootEntity(sourceRootEntity, false, "", source)
      builder.addJavaModuleSettingsEntity(true, true, null, null, module, source)
    }
  }

  @Test
  fun `remove module`() {
    checkSaveProjectAfterChange("directoryBased/removeModule", "fileBased/removeModule") { builder, _ ->
      val utilModule = builder.entities(ModuleEntity::class).first { it.name == "util" }
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
    checkSaveProjectAfterChange("directoryBased/modifyLibrary", "fileBased/modifyLibrary") { builder, projectDirUrl ->
      val junitLibrary = builder.entities(LibraryEntity::class).first { it.name == "junit" }
      val root = LibraryRoot(VirtualFileUrlManager.fromUrl("jar://${JpsPathUtil.urlToPath(projectDirUrl)}/lib/junit2.jar!/"),
                             LibraryRootTypeId("CLASSES"), LibraryRoot.InclusionOptions.ROOT_ITSELF)
      builder.modifyEntity(ModifiableLibraryEntity::class.java, junitLibrary) {
        roots = listOf(root)
      }
    }
  }

  @Test
  fun `remove library in directory-based project`() {
    checkSaveProjectAfterChange(sampleDirBasedProjectFile, null, listOf(".idea/libraries/junit.xml")) { builder, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class).first { it.name == "junit" }
      builder.removeEntity(junitLibrary)
    }
  }

  @Test
  fun `remove library in file-based project`() {
    checkSaveProjectAfterChange(sampleFileBasedProjectFile, "fileBased/removeLibrary", emptyList()) { builder, _ ->
      val junitLibrary = builder.entities(LibraryEntity::class).first { it.name == "junit" }
      builder.removeEntity(junitLibrary)
    }
  }

  private fun checkSaveProjectAfterChange(directoryNameForDirectoryBased: String,
                                          directoryNameForFileBased: String,
                                          change: (TypedEntityStorageBuilder, String) -> Unit) {
    checkSaveProjectAfterChange(sampleDirBasedProjectFile, directoryNameForDirectoryBased, emptyList(), change)
    checkSaveProjectAfterChange(sampleFileBasedProjectFile, directoryNameForFileBased, emptyList(), change)
  }

  private fun checkSaveProjectAfterChange(originalProjectFile: File, changedFilesDirectoryName: String?,
                                          pathsToRemove: List<String>,
                                          change: (TypedEntityStorageBuilder, String) -> Unit) {
    val projectData = copyAndLoadProject(originalProjectFile)
    val builder = TypedEntityStorageBuilder.from(projectData.storage)
    change(builder, projectData.projectDirUrl)
    val changesMap = builder.collectChanges(projectData.storage)
    val changedSources = changesMap.values.flatMapTo(HashSet()) { changes -> changes.flatMap { change ->
      when (change) {
        is EntityChange.Added -> listOf(change.entity)
        is EntityChange.Removed -> listOf(change.entity)
        is EntityChange.Replaced -> listOf(change.oldEntity, change.newEntity)
      }
    }.map { it.entitySource }}
    val writer = JpsFileContentWriterImpl()
    val sourceToUpdate = projectData.serializationData.saveEntities(builder.toStorage(), changedSources, writer)
    sourceToUpdate.forEach {
      builder.changeSource(it.first, it.second)
    }
    writer.writeFiles(projectData.projectDir)
    projectData.serializationData.checkConsistency(projectData.projectDirUrl, builder.toStorage())

    val expectedDir = FileUtil.createTempDirectory("jpsProjectTest", "expected")
    FileUtil.copyDir(projectData.originalProjectDir, expectedDir)
    if (changedFilesDirectoryName != null) {
      val changedDir = PathManagerEx.findFileUnderCommunityHome("platform/workspaceModel-ide/testData/serialization/reload/$changedFilesDirectoryName")
      FileUtil.copyDir(changedDir, expectedDir)
    }
    pathsToRemove.forEach {
      FileUtil.delete(File(expectedDir, it))
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