// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.toConfigLocation
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addSourceRootEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.*

class ReplaceBySourceTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
  }

  @Test
  fun checkOrderAtReplaceBySourceNotDependsOnExecutionCount() {
    val expectedResult = mutableListOf<String>()
    for (i in 1..100) {
      val builder = createBuilder()
      val storage = MutableEntityStorage.create()
      storage.replaceBySource({ entitySource -> entitySource is JpsFileEntitySource }, builder)
      val contentRootEntity = storage.entities(ContentRootEntity::class.java).first()
      if (i == 1) {
        contentRootEntity.sourceRoots.forEach { expectedResult.add(it.url.toString()) }
      } else {
        Assert.assertArrayEquals(expectedResult.toTypedArray(), contentRootEntity.sourceRoots.map { it.url.toString() }.toList().toTypedArray())
      }
    }
  }

  private fun createBuilder(): MutableEntityStorage {
    val fileUrl = "/user/opt/app/a.txt"
    val fileUrl2 = "/user/opt/app/b.txt"
    val fileUrl3 = "/user/opt/app/c.txt"
    val fileUrl4 = "/user/opt/app/d.txt"
    val fileUrl5 = "/user/opt/app/e.txt"

    val builder = MutableEntityStorage.create()
    val baseDir = projectModel.baseProjectDir.rootPath.resolve("test")
    val iprFile = baseDir.resolve("testProject.ipr")
    val configLocation = toConfigLocation(iprFile, virtualFileManager)
    val source = JpsFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)


    val moduleEntity = builder.addModuleEntity("name", emptyList(), source)
    val contentRootEntity = builder.addContentRootEntity(virtualFileManager.fromUrl(fileUrl), emptyList(), emptyList(), moduleEntity)
    builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(fileUrl2), "", source)
    builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(fileUrl3), "", source)
    builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(fileUrl4), "", source)
    builder.addSourceRootEntity(contentRootEntity, virtualFileManager.fromUrl(fileUrl5), "", source)
    return builder
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}