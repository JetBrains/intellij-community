// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.toConfigLocation
import org.junit.*

class ReplaceBySourceTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()
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
      }
      else {
        Assert.assertArrayEquals(expectedResult.toTypedArray(),
                                 contentRootEntity.sourceRoots.map { it.url.toString() }.toList().toTypedArray())
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
    val source = JpsProjectFileEntitySource.FileInDirectory(configLocation.baseDirectoryUrl, configLocation)


    val moduleEntity = builder addEntity ModuleEntity("name", emptyList(), source)
    val contentRootEntity = builder addEntity ContentRootEntity(virtualFileManager.getOrCreateFromUri(fileUrl), emptyList<@NlsSafe String>(),
                                                                moduleEntity.entitySource) {
      module = moduleEntity
    }
    builder addEntity SourceRootEntity(virtualFileManager.getOrCreateFromUri(fileUrl2), "", source) {
      contentRoot = contentRootEntity
    }
    builder addEntity SourceRootEntity(virtualFileManager.getOrCreateFromUri(fileUrl3), "", source) {
      contentRoot = contentRootEntity
    }
    builder addEntity SourceRootEntity(virtualFileManager.getOrCreateFromUri(fileUrl4), "", source) {
      contentRoot = contentRootEntity
    }
    builder addEntity SourceRootEntity(virtualFileManager.getOrCreateFromUri(fileUrl5), "", source) {
      contentRoot = contentRootEntity
    }
    return builder
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}