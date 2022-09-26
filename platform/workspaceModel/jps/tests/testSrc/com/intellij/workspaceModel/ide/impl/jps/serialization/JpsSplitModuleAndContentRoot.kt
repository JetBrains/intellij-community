package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class JpsSplitModuleAndContentRoot {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = IdeVirtualFileUrlManagerImpl()
  }

  @Test
  fun `add local content root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  // There is some issue with path storing, they add additional ../.. in tests. I won't investigate it right now
  @Test
  fun `add second local content root`() {
    checkSaveProjectAfterChange("before/addSecondContentRoot", "after/addSecondContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }
  @Test
  fun `add local content and source root`() {
    checkSaveProjectAfterChange("before/addSourceRoot", "after/addSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val contentRootEntity = builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity,
                                                              (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add local source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addCustomSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `load content root`() {
    checkSaveProjectAfterChange("after/addContentRoot", "after/addContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoot = moduleEntity.contentRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRoot.entitySource is JpsFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load content root with two roots`() {
    checkSaveProjectAfterChange("after/addSecondContentRoot", "after/addSecondContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      assertEquals(2, contentRoots.size)
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)

      // Will throw an exception if something is wrong
      contentRoots.single { it.entitySource is JpsFileEntitySource.FileInDirectory }
      contentRoots.single { it.entitySource is JpsImportedEntitySource }
    }
  }

  @Test
  fun `load custom content and source root`() {
    checkSaveProjectAfterChange("after/addSourceRoot", "after/addSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoot = moduleEntity.contentRoots.single()
      val sourceRoot = contentRoot.sourceRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRoot.entitySource is JpsFileEntitySource.FileInDirectory)
      assertTrue(sourceRoot.entitySource is JpsFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load local source root`() {
    checkSaveProjectAfterChange("after/addCustomSourceRoot2", "after/addCustomSourceRoot2") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoot = contentRootEntity.sourceRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(sourceRoot.entitySource is JpsFileEntitySource.FileInDirectory)
    }
  }

  private fun checkSaveProjectAfterChange(dirBefore: String,
                                          dirAfter: String,
                                          change: (MutableEntityStorage, JpsProjectConfigLocation) -> Unit) {

    val initialDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspaceModel/jps/tests/testData/serialization/splitModuleAndContentRoot/$dirBefore")
    checkSaveProjectAfterChange(initialDir, dirAfter, change)
  }

  private fun checkSaveProjectAfterChange(originalProjectFile: File, changedFilesDirectoryName: String?,
                                          change: (MutableEntityStorage, JpsProjectConfigLocation) -> Unit) {
    val projectData = copyAndLoadProject(originalProjectFile, virtualFileManager)
    val builder = MutableEntityStorage.from(projectData.storage)
    change(builder, projectData.configLocation)
    val changesMap = builder.collectChanges(projectData.storage)
    val changedSources = changesMap.values.flatMapTo(HashSet()) { changes ->
      changes.flatMap { change ->
        when (change) {
          is EntityChange.Added -> listOf(change.entity)
          is EntityChange.Removed -> listOf(change.entity)
          is EntityChange.Replaced -> listOf(change.oldEntity, change.newEntity)
        }
      }.map { it.entitySource }
    }
    val writer = JpsFileContentWriterImpl(projectData.configLocation)
    projectData.serializers.saveEntities(builder.toSnapshot(), changedSources, writer)
    writer.writeFiles()
    //projectData.serializers.checkConsistency(projectData.configLocation, builder.toSnapshot(), virtualFileManager)

    val expectedDir = FileUtil.createTempDirectory("jpsProjectTest", "expected")
    FileUtil.copyDir(projectData.originalProjectDir, expectedDir)
    if (changedFilesDirectoryName != null) {
      val changedDir = PathManagerEx.findFileUnderCommunityHome(
        "platform/workspaceModel/jps/tests/testData/serialization/splitModuleAndContentRoot/$changedFilesDirectoryName")
      FileUtil.copyDir(changedDir, expectedDir)
    }
    expectedDir.walk().filter { it.isFile && it.readText().trim() == "<delete/>" }.forEach {
      FileUtil.delete(it)
    }

    assertDirectoryMatches(projectData.projectDir, expectedDir,
                           emptySet(),
                           emptyList())
  }

  internal fun copyAndLoadProject(originalProjectFile: File, virtualFileManager: VirtualFileUrlManager): LoadedProjectData {
    val (projectDir, originalProjectDir) = copyProjectFiles(originalProjectFile)
    val externalStorageConfigurationManager = ExternalStorageConfigurationManager.getInstance(projectModel.project)
    externalStorageConfigurationManager.isEnabled = true
    val originalBuilder = MutableEntityStorage.create()
    val projectFile = if (originalProjectFile.isFile) File(projectDir, originalProjectFile.name) else projectDir
    val configLocation = toConfigLocation(projectFile.toPath(), virtualFileManager)
    val serializers = loadProject(configLocation, originalBuilder, virtualFileManager, externalStorageConfigurationManager = externalStorageConfigurationManager) as JpsProjectSerializersImpl
    val loadedProjectData = LoadedProjectData(originalBuilder.toSnapshot(), serializers, configLocation, originalProjectDir)
    //serializers.checkConsistency(loadedProjectData.configLocation, loadedProjectData.storage, virtualFileManager)
    return loadedProjectData
  }


  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}