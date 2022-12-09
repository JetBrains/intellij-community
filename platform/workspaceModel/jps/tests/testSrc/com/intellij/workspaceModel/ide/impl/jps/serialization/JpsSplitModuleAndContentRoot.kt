package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.JpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

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

  @Test
  fun `add local content root local save`() {
    checkSaveProjectAfterChange("before/addContentRootLocalSave", "after/addContentRootLocalSave", false) { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add multiple local content roots`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addMultipleContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addContentRootEntity(virtualFileManager.fromPath(path2), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add external content root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addExternalContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity, moduleEntity.entitySource)
    }
  }

  @Test
  fun `add mixed content root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addMixedContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity, moduleEntity.entitySource)
      builder.addContentRootEntity(virtualFileManager.fromPath(path2), emptyList(), emptyList(), moduleEntity,
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
  fun `add multiple local source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addMultipleCustomSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path2), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add external source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addExternalCustomSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path), "mock", moduleEntity.entitySource)
    }
  }

  @Test
  fun `add mixed source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addMixedCustomSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path), "mock", moduleEntity.entitySource)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path2), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add custom content and source root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addContentAndSourceRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      val contentRootEntity = builder.addContentRootEntity(virtualFileManager.fromPath(path), emptyList(), emptyList(), moduleEntity,
                                                           (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromPath(path2), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add local exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addExcludeRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `add multiple local exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addMultipleExcludeRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path2),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `add multiple local exclude to multiple content roots`() {
    checkSaveProjectAfterChange("before/addExcludeRootsToMultipleContentRoots",
                                "after/addExcludeRootsToMultipleContentRoots") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single { it.url.url.endsWith("myContentRoot") }
      val contentRootEntity2 = moduleEntity.contentRoots.single { it.url.url.endsWith("myContentRoot2") }
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot/x")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2/y")
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path2),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity2
      }
    }
  }

  @Test
  fun `add external exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addExternalExcludeRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path), moduleEntity.entitySource) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `add mixed exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addMixedExcludeRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val path = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot")
      val path2 = JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString + "/myContentRoot2")
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromPath(path2), moduleEntity.entitySource) {
        this.contentRoot = contentRootEntity
      }
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
  fun `load external content root`() {
    checkSaveProjectAfterChange("after/addExternalContentRootLoading", "after/addExternalContentRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRoots.single().entitySource is JpsImportedEntitySource)
    }
  }

  @Test
  fun `load mixed content root`() {
    checkSaveProjectAfterChange("after/addMixedContentRootLoading", "after/addMixedContentRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      assertEquals(2, contentRoots.size)
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
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

  @Test
  fun `load multiple source root`() {
    checkSaveProjectAfterChange("after/addMultipleCustomSourceRootLoading",
                                "after/addMultipleCustomSourceRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoots = contentRootEntity.sourceRoots
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, sourceRoots.size)
      assertTrue(sourceRoots.all { it.entitySource is JpsFileEntitySource.FileInDirectory })
    }
  }

  @Test
  fun `load external source root`() {
    checkSaveProjectAfterChange("after/addExternalCustomSourceRootLoading",
                                "after/addExternalCustomSourceRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoot = contentRootEntity.sourceRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(sourceRoot.entitySource is JpsImportedEntitySource)
    }
  }

  @Test
  fun `load mixed source root`() {
    checkSaveProjectAfterChange("after/addMixedCustomSourceRootLoading",
                                "after/addMixedCustomSourceRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoots = contentRootEntity.sourceRoots
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, sourceRoots.size)
      assertTrue(sourceRoots.singleOrNull { it.entitySource is JpsFileEntitySource.FileInDirectory } != null)
      assertTrue(sourceRoots.singleOrNull { it.entitySource is JpsImportedEntitySource } != null)
    }
  }

  @Test
  fun `load separate exclude roots`() {
    checkSaveProjectAfterChange("after/addExcludeRootLoading", "after/addExcludeRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(exclude.entitySource is JpsFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load multiple local exclude`() {
    checkSaveProjectAfterChange("after/addMultipleExcludeRootLoading", "after/addMultipleExcludeRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(exclude.all { it.entitySource is JpsFileEntitySource.FileInDirectory })
    }
  }

  @Test
  fun `load multiple local exclude to multiple content roots`() {
    checkSaveProjectAfterChange("after/addExcludeRootsToMultipleContentRootsLoading",
                                "after/addExcludeRootsToMultipleContentRootsLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      val exclude = contentRoots.flatMap { it.excludedUrls }
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, contentRoots.size)
      assertEquals(2, exclude.size)
      assertTrue(contentRoots.all { it.entitySource is JpsImportedEntitySource })
      assertTrue(exclude.all { it.entitySource is JpsFileEntitySource.FileInDirectory })
    }
  }

  @Test
  fun `load external exclude`() {
    checkSaveProjectAfterChange("after/addExternalExcludeRootLoading", "after/addExternalExcludeRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(exclude.entitySource is JpsImportedEntitySource)
    }
  }

  @Test
  fun `load mixed exclude`() {
    checkSaveProjectAfterChange("after/addMixedExcludeRootLoading", "after/addMixedExcludeRootLoading") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, exclude.size)
      assertTrue(exclude.singleOrNull { it.entitySource is JpsFileEntitySource.FileInDirectory } != null)
      assertTrue(exclude.singleOrNull { it.entitySource is JpsImportedEntitySource } != null)
    }
  }

  @Test
  fun `jdk does not dissapear`() {
    checkSaveProjectAfterChange("after/jdkIsNotRemoved", "after/jdkIsNotRemoved") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      assertEquals(2, moduleEntity.dependencies.size)
    }
  }

  @Test
  fun `check output directory`() {
    checkSaveProjectAfterChange("after/addContentRoot", "after/addContentRoot") { builder, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      assertTrue(moduleEntity.javaSettings!!.inheritedCompilerOutput)
    }
  }

  @Test
  fun `add custom facet`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addCustomFacet") { builder, configLocation ->
      val mockFacetType = MockFacetType()
      registerFacetType(mockFacetType, projectModel.disposableRule.disposable)
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      builder addEntity FacetEntity("MyFacet", MockFacetType.ID.toString(), moduleEntity.symbolicId, getInternalFileSource(moduleEntity.entitySource)!!) {
        this.module = moduleEntity
      }
    }
  }

  @Test
  fun `load custom facet`() {
    checkSaveProjectAfterChange("after/addCustomFacet", "after/addCustomFacet") { builder, configLocation ->
      val mockFacetType = MockFacetType()
      registerFacetType(mockFacetType, projectModel.disposableRule.disposable)
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val facetEntity = moduleEntity.facets.single()
      assertEquals("MyFacet", facetEntity.name)
    }
  }

  private fun checkSaveProjectAfterChange(dirBefore: String,
                                          dirAfter: String,
                                          externalStorage: Boolean = true,
                                          change: (MutableEntityStorage, JpsProjectConfigLocation) -> Unit) {

    val initialDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspaceModel/jps/tests/testData/serialization/splitModuleAndContentRoot/$dirBefore")
    val externalStorageConfigurationManager = ExternalStorageConfigurationManager.getInstance(projectModel.project)
    externalStorageConfigurationManager.isEnabled = externalStorage
    checkSaveProjectAfterChange(initialDir, dirAfter, change, virtualFileManager, "serialization/splitModuleAndContentRoot", false,
                                externalStorageConfigurationManager)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}