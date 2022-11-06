// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import com.intellij.workspaceModel.storage.entities.test.api.AnotherSource
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ContentRootEqualityTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `rbs with events`() {
    val builder1 = createEmptyBuilder()
    builder1.addEntity(ModuleEntity("MyName", emptyList(), MySource) {
      contentRoots = listOf(
        ContentRootEntity(virtualFileManager.fromUrl("/123"), emptyList(), MySource) {
          sourceRoots = listOf(
            SourceRootEntity(virtualFileManager.fromUrl("/data"), "Type", AnotherSource),
          )
        },
      )
    })

    val builder2 = createEmptyBuilder()
    builder2.addEntity(ModuleEntity("MyName", emptyList(), MySource) {
      contentRoots = listOf(
        ContentRootEntity(virtualFileManager.fromUrl("/123"), emptyList(), MySource) {
          this.excludedUrls = listOf(ExcludeUrlEntity(virtualFileManager.fromUrl("/myRoot"), this.entitySource))
        },
      )
    })
    builder1.changeLog.clear()

    builder1.replaceBySource({ it is MySource }, builder2)

    assertEquals("Type", builder1.entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single().rootType)
  }
}