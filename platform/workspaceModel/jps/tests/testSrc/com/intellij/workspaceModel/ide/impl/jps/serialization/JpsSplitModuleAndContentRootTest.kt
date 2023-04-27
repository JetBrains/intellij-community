// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.platform.workspaceModel.jps.JpsImportedEntitySource
import com.intellij.platform.workspaceModel.jps.JpsProjectConfigLocation
import com.intellij.platform.workspaceModel.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspaceModel.jps.OrphanageWorkerEntitySource
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JpsSplitModuleAndContentRootTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    Assume.assumeTrue(EntitiesOrphanage.isEnabled)
    virtualFileManager = IdeVirtualFileUrlManagerImpl()
  }

  @Test
  fun `add local content root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add local content root via orphanage`() {
    checkSaveProjectAfterChange("after/addContentRootOrphanage", "after/addContentRootOrphanage", false) { builder, orphanage, configLocation ->
      assertTrue(builder.entities(ModuleEntity::class.java).toList().isEmpty())
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().entitySource !is OrphanageWorkerEntitySource)
    }
  }

  @Test
  fun `add local source root via orphanage`() {
    Assume.assumeTrue(EntitiesOrphanage.isEnabled)
    checkSaveProjectAfterChange("after/addSourceRootOrphanage", "after/addSourceRootOrphanage", false) { builder, orphanage, configLocation ->
      assertTrue(builder.entities(ModuleEntity::class.java).toList().isEmpty())
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().entitySource is OrphanageWorkerEntitySource)
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single().entitySource !is OrphanageWorkerEntitySource)
    }
  }

  @Test
  fun `add local content and source root via orphanage`() {
    Assume.assumeTrue(EntitiesOrphanage.isEnabled)
    checkSaveProjectAfterChange("after/addSourceAndContentRootOrphanage", "after/addSourceAndContentRootOrphanage", false) { builder, orphanage, configLocation ->
      assertTrue(builder.entities(ModuleEntity::class.java).toList().isEmpty())
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().entitySource !is OrphanageWorkerEntitySource)
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single().entitySource !is OrphanageWorkerEntitySource)
    }
  }

  @Test
  fun `add local exclude via orphanage`() {
    Assume.assumeTrue(EntitiesOrphanage.isEnabled)
    checkSaveProjectAfterChange("after/addExcludeOrphanage", "after/addExcludeOrphanage", false) { builder, orphanage, configLocation ->
      assertTrue(builder.entities(ModuleEntity::class.java).toList().isEmpty())
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().entitySource is OrphanageWorkerEntitySource)
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().excludedUrls.single().entitySource !is OrphanageWorkerEntitySource)
    }
  }

  @Test
  fun `add local exclude and content root via orphanage`() {
    Assume.assumeTrue(EntitiesOrphanage.isEnabled)
    checkSaveProjectAfterChange("after/addExcludeAndContentRootOrphanage", "after/addExcludeAndContentRootOrphanage", false) { builder, orphanage, configLocation ->
      assertTrue(builder.entities(ModuleEntity::class.java).toList().isEmpty())
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().entitySource !is OrphanageWorkerEntitySource)
      assertTrue(orphanage.entities(ModuleEntity::class.java).single().contentRoots.single().excludedUrls.single().entitySource !is OrphanageWorkerEntitySource)
    }
  }

  @Test
  fun `add local content root local save`() {
    checkSaveProjectAfterChange("before/addContentRootLocalSave", "after/addContentRootLocalSave", false) { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add multiple local content roots`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addMultipleContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addContentRootEntity(virtualFileManager.fromUrl(url2), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add external content root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addExternalContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity, moduleEntity.entitySource)
    }
  }

  @Test
  fun `add mixed content root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addMixedContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity, moduleEntity.entitySource)
      builder.addContentRootEntity(virtualFileManager.fromUrl(url2), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  // There is some issue with path storing, they add additional ../.. in tests. I won't investigate it right now
  @Test
  fun `add second local content root`() {
    checkSaveProjectAfterChange("before/addSecondContentRoot", "after/addSecondContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity,
                                   (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add local content and source root`() {
    checkSaveProjectAfterChange("before/addSourceRoot", "after/addSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val contentRootEntity = builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity,
                                                           (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add local source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addCustomSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add multiple local source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addMultipleCustomSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url2), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add external source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addExternalCustomSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url), "mock", moduleEntity.entitySource)
    }
  }

  @Test
  fun `add mixed source root`() {
    checkSaveProjectAfterChange("before/addCustomSourceRoot", "after/addMixedCustomSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url), "mock", moduleEntity.entitySource)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url2), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add custom content and source root`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addContentAndSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      val contentRootEntity = builder.addContentRootEntity(virtualFileManager.fromUrl(url), emptyList(), emptyList(), moduleEntity,
                                                           (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
      builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(url2), "mock",
                                  (moduleEntity.entitySource as JpsImportedEntitySource).internalFile)
    }
  }

  @Test
  fun `add local exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `add multiple local exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addMultipleExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url2),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `add multiple local exclude to multiple content roots`() {
    checkSaveProjectAfterChange("before/addExcludeRootsToMultipleContentRoots",
                                "after/addExcludeRootsToMultipleContentRoots") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single { it.url.url.endsWith("myContentRoot") }
      val contentRootEntity2 = moduleEntity.contentRoots.single { it.url.url.endsWith("myContentRoot2") }
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot/x"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2/y"
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url2),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity2
      }
    }
  }

  @Test
  fun `add external exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addExternalExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url), moduleEntity.entitySource) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `add mixed exclude`() {
    checkSaveProjectAfterChange("before/addExcludeRoot", "after/addMixedExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val url = configLocation.baseDirectoryUrlString + "/myContentRoot"
      val url2 = configLocation.baseDirectoryUrlString + "/myContentRoot2"
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url),
                                         (moduleEntity.entitySource as JpsImportedEntitySource).internalFile) {
        this.contentRoot = contentRootEntity
      }
      builder addEntity ExcludeUrlEntity(virtualFileManager.fromUrl(url2), moduleEntity.entitySource) {
        this.contentRoot = contentRootEntity
      }
    }
  }

  @Test
  fun `load content root`() {
    checkSaveProjectAfterChange("after/addContentRoot", "after/addContentRoot") { builder, orphanage, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoot = moduleEntity.contentRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRoot.entitySource is JpsProjectFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load content root with two roots`() {
    checkSaveProjectAfterChange("after/addSecondContentRoot", "after/addSecondContentRoot") { builder, orphanage, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      assertEquals(2, contentRoots.size)
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)

      // Will throw an exception if something is wrong
      contentRoots.single { it.entitySource is JpsProjectFileEntitySource.FileInDirectory }
      contentRoots.single { it.entitySource is JpsImportedEntitySource }
    }
  }

  @Test
  fun `load external content root`() {
    checkSaveProjectAfterChange("after/addExternalContentRoot", "after/addExternalContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRoots.single().entitySource is JpsImportedEntitySource)
    }
  }

  @Test
  fun `load mixed content root`() {
    checkSaveProjectAfterChange("after/addMixedContentRoot", "after/addMixedContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      assertEquals(2, contentRoots.size)
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      contentRoots.single { it.entitySource is JpsProjectFileEntitySource.FileInDirectory }
      contentRoots.single { it.entitySource is JpsImportedEntitySource }
    }
  }

  @Test
  fun `load custom content and source root`() {
    checkSaveProjectAfterChange("after/addSourceRoot", "after/addSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoot = moduleEntity.contentRoots.single()
      val sourceRoot = contentRoot.sourceRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRoot.entitySource is JpsProjectFileEntitySource.FileInDirectory)
      assertTrue(sourceRoot.entitySource is JpsProjectFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load local source root`() {
    checkSaveProjectAfterChange("after/addCustomSourceRoot2", "after/addCustomSourceRoot2") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoot = contentRootEntity.sourceRoots.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(sourceRoot.entitySource is JpsProjectFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load multiple source root`() {
    checkSaveProjectAfterChange("after/addMultipleCustomSourceRoot",
                                "after/addMultipleCustomSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoots = contentRootEntity.sourceRoots
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, sourceRoots.size)
      assertTrue(sourceRoots.all { it.entitySource is JpsProjectFileEntitySource.FileInDirectory })
    }
  }

  @Test
  fun `load external source root`() {
    checkSaveProjectAfterChange("after/addExternalCustomSourceRoot",
                                "after/addExternalCustomSourceRoot") { builder, _, configLocation ->
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
    checkSaveProjectAfterChange("after/addMixedCustomSourceRoot",
                                "after/addMixedCustomSourceRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val sourceRoots = contentRootEntity.sourceRoots
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, sourceRoots.size)
      assertTrue(sourceRoots.singleOrNull { it.entitySource is JpsProjectFileEntitySource.FileInDirectory } != null)
      assertTrue(sourceRoots.singleOrNull { it.entitySource is JpsImportedEntitySource } != null)
    }
  }

  @Test
  fun `load separate exclude roots`() {
    checkSaveProjectAfterChange("after/addExcludeRoot", "after/addExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls.single()
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(exclude.entitySource is JpsProjectFileEntitySource.FileInDirectory)
    }
  }

  @Test
  fun `load multiple local exclude`() {
    checkSaveProjectAfterChange("after/addMultipleExcludeRoot", "after/addMultipleExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertTrue(exclude.all { it.entitySource is JpsProjectFileEntitySource.FileInDirectory })
    }
  }

  @Test
  fun `load multiple local exclude to multiple content roots`() {
    checkSaveProjectAfterChange("after/addExcludeRootsToMultipleContentRoots",
                                "after/addExcludeRootsToMultipleContentRoots") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRoots = moduleEntity.contentRoots
      val exclude = contentRoots.flatMap { it.excludedUrls }
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, contentRoots.size)
      assertEquals(2, exclude.size)
      assertTrue(contentRoots.all { it.entitySource is JpsImportedEntitySource })
      assertTrue(exclude.all { it.entitySource is JpsProjectFileEntitySource.FileInDirectory })
    }
  }

  @Test
  fun `load external exclude`() {
    checkSaveProjectAfterChange("after/addExternalExcludeRoot", "after/addExternalExcludeRoot") { builder, _, configLocation ->
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
    checkSaveProjectAfterChange("after/addMixedExcludeRoot", "after/addMixedExcludeRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls
      assertTrue(moduleEntity.entitySource is JpsImportedEntitySource)
      assertTrue(contentRootEntity.entitySource is JpsImportedEntitySource)
      assertEquals(2, exclude.size)
      assertTrue(exclude.singleOrNull { it.entitySource is JpsProjectFileEntitySource.FileInDirectory } != null)
      assertTrue(exclude.singleOrNull { it.entitySource is JpsImportedEntitySource } != null)
    }
  }

  @Test
  fun `load mixed exclude 2`() {
    checkSaveProjectAfterChange("after/addExcludeWithDifferentOrder1", "after/addExcludeWithDifferentOrder1") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls
      assertEquals(2, exclude.size)
    }
  }

  @Test
  fun `load mixed exclude 3`() {
    checkSaveProjectAfterChange("after/addExcludeWithDifferentOrder2", "after/addExcludeWithDifferentOrder2") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val contentRootEntity = moduleEntity.contentRoots.single()
      val exclude = contentRootEntity.excludedUrls
      assertEquals(2, exclude.size)
    }
  }

  @Test
  fun `jdk does not dissapear`() {
    checkSaveProjectAfterChange("after/jdkIsNotRemoved", "after/jdkIsNotRemoved") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      assertEquals(2, moduleEntity.dependencies.size)
    }
  }

  @Test
  fun `check output directory`() {
    checkSaveProjectAfterChange("after/addContentRoot", "after/addContentRoot") { builder, _, configLocation ->
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      assertTrue(moduleEntity.javaSettings!!.inheritedCompilerOutput)
    }
  }

  @Test
  fun `add custom facet`() {
    checkSaveProjectAfterChange("before/addContentRoot", "after/addCustomFacet") { builder, _, configLocation ->
      val mockFacetType = MockFacetType()
      registerFacetType(mockFacetType, projectModel.disposableRule.disposable)
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      builder addEntity FacetEntity("MyFacet", moduleEntity.symbolicId, MockFacetType.ID.toString(),
                                    getInternalFileSource(moduleEntity.entitySource)!!) {
        this.module = moduleEntity
      }
    }
  }

  @Test
  fun `load custom facet`() {
    checkSaveProjectAfterChange("after/addCustomFacet", "after/addCustomFacet") { builder, _, configLocation ->
      val mockFacetType = MockFacetType()
      registerFacetType(mockFacetType, projectModel.disposableRule.disposable)
      val moduleEntity = builder.entities(ModuleEntity::class.java).single()
      val facetEntity = moduleEntity.facets.single()
      assertEquals("MyFacet", facetEntity.name)
    }
  }

  @TestFor(classes = [JavaModuleSettingsEntity::class, ModuleImlFileEntitiesSerializer::class, JavaSettingsSerializer::class])
  @Test
  fun `load module without java custom settings`() {
    checkSaveProjectAfterChange("after/imlWithoutJavaSettings", "after/imlWithoutJavaSettings") { builder, orphanage, _ ->
      val javaSettings = builder.entities(ModuleEntity::class.java).single().javaSettings
      assertNull(javaSettings)
    }
  }

  @TestFor(classes = [JavaModuleSettingsEntity::class, ModuleImlFileEntitiesSerializer::class, JavaSettingsSerializer::class])
  @Test
  fun `load module without java custom settings but with exclude`() {
    checkSaveProjectAfterChange("after/imlWithoutJavaSettingsButWithExclude", "after/imlWithoutJavaSettingsButWithExclude") { builder, _, _ ->
      val javaSettings = builder.entities(ModuleEntity::class.java).single().javaSettings
      assertNotNull(javaSettings)
      assertTrue(javaSettings.excludeOutput)
      assertFalse(javaSettings.inheritedCompilerOutput)
      assertNull(javaSettings.languageLevelId)
      assertNull(javaSettings.compilerOutputForTests)
    }
  }

  @TestFor(classes = [JavaModuleSettingsEntity::class, ModuleImlFileEntitiesSerializer::class, JavaSettingsSerializer::class])
  @Test
  fun `load module without java custom settings but with languageLevel`() {
    checkSaveProjectAfterChange("after/imlWithoutJavaSettingsButWithLanguageLevel",
                                "after/imlWithoutJavaSettingsButWithLanguageLevel") { builder, _, _ ->
      val javaSettings = builder.entities(ModuleEntity::class.java).single().javaSettings
      assertNotNull(javaSettings)
      assertFalse(javaSettings.excludeOutput)
      assertFalse(javaSettings.inheritedCompilerOutput)
      assertEquals("JDK_1_8", javaSettings.languageLevelId)
      assertNull(javaSettings.compilerOutputForTests)
    }
  }

  @Test
  fun `load incorrect saved additional root`() {
    checkSaveProjectAfterChange("before/loadIncorrectSavedAdditionalRoots", "after/loadIncorrectSavedAdditionalRoots", forceFilesRewrite = true) { builder, orphanage, configLocation ->
      // Nothing
    }
  }

  @Test
  fun `load and remove additional root`() {
    checkSaveProjectAfterChange("before/loadAndRemoveAdditionalRoot", "after/loadAndRemoveAdditionalRootY", forceFilesRewrite = true) { builder, orphanage, configLocation ->
      val toRemove = builder.entities(ModuleEntity::class.java).single().contentRoots.filter { it.entitySource !is JpsImportedEntitySource }
      toRemove.forEach { builder.removeEntity(it) }
    }
  }

  private fun checkSaveProjectAfterChange(dirBefore: String,
                                          dirAfter: String,
                                          externalStorage: Boolean = true,
                                          forceFilesRewrite: Boolean = false,
                                          change: (MutableEntityStorage, MutableEntityStorage, JpsProjectConfigLocation) -> Unit) {

    val initialDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspaceModel/jps/tests/testData/serialization/splitModuleAndContentRoot/$dirBefore")
    val externalStorageConfigurationManager = ExternalStorageConfigurationManager.getInstance(projectModel.project)
    externalStorageConfigurationManager.isEnabled = externalStorage
    checkSaveProjectAfterChange(initialDir, dirAfter, { builder, orphanage, _, location -> change(builder, orphanage, location) },
                                UnloadedModulesNameHolder.DUMMY,
                                virtualFileManager, "serialization/splitModuleAndContentRoot", false,
                                externalStorageConfigurationManager, forceAllFilesRewrite = forceFilesRewrite)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}