// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class ContentRootEqualityTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  @Ignore
  fun `content roots with same urls`() {
    // create module with content root but without source roots
    val contentRootUrl = virtualFileManager.getOrCreateFromUrl("/123")
    val sourceRootUrl = virtualFileManager.getOrCreateFromUrl("/data")

    val targetBuilder = MutableEntityStorage.create()
    val module = targetBuilder.addEntity(ModuleEntity("MyName", emptyList(), MySource) {
      contentRoots = listOf(
        ContentRootEntity(contentRootUrl, emptyList(), MySource) {
          sourceRoots = emptyList()
        },
      )
    })

    // create another builder
    val sourceBuilder = MutableEntityStorage.from(targetBuilder.toSnapshot())

    // add source root to the target storage
    targetBuilder.modifyContentRootEntity(module.contentRoots.first()) {
      sourceRoots = listOf(SourceRootEntity(sourceRootUrl, SourceRootTypeId("Type"), MySource))
    }

    // remove content root from the source storage
    sourceBuilder.removeEntity(sourceBuilder.entities(ContentRootEntity::class.java).first())

    targetBuilder.assertConsistency()
    sourceBuilder.assertConsistency()

    // apply target storage to the source storage
    targetBuilder.applyChangesFrom(sourceBuilder)
  }

  @Test
  fun `rbs with events`() {
    val builder1 = MutableEntityStorage.create()
    builder1.addEntity(ModuleEntity("MyName", emptyList(), MySource) {
      contentRoots = listOf(
        ContentRootEntity(virtualFileManager.getOrCreateFromUrl("/123"), emptyList(), MySource) {
          sourceRoots = listOf(
            SourceRootEntity(virtualFileManager.getOrCreateFromUrl("/data"), SourceRootTypeId("Type"), AnotherSource),
          )
        },
      )
    })

    val builder2 = MutableEntityStorage.create()
    builder2.addEntity(ModuleEntity("MyName", emptyList(), MySource) {
      contentRoots = listOf(
        ContentRootEntity(virtualFileManager.getOrCreateFromUrl("/123"), emptyList(), MySource) {
          this.excludedUrls = listOf(ExcludeUrlEntity(virtualFileManager.getOrCreateFromUrl("/myRoot"), this.entitySource))
        },
      )
    })

    builder1.replaceBySource({ it is MySource }, builder2)

    assertEquals(SourceRootTypeId("Type"), builder1.entities<ModuleEntity>().single().contentRoots.single().sourceRoots.single().rootTypeId)
  }
}