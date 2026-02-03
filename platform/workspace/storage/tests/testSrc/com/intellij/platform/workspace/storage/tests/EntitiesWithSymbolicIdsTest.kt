// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.PlaceholderEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.children
import com.intellij.platform.workspace.storage.toBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class EntitiesWithSymbolicIdsTest {
  @Test
  fun `test workspace model parent-child references`(): Unit = runBlocking {
    var workspaceModel = createEmptyBuilder().toSnapshot()

    val tempStorage = ImmutableEntityStorage.empty().update { builder ->
      val entitySource = SampleEntitySource("1")
      builder addEntity PlaceholderEntity("test", entitySource)
    }

    // First WSM update
    workspaceModel = workspaceModel.update { projectBuilder ->
      projectBuilder.replaceBySource({ true }, tempStorage)
    }

    var storage = ImmutableEntityStorage.empty()

    storage = storage.update { builder ->
      val entitySource = SampleEntitySource("2")
      builder addEntity GrandParentWithId("super.parent", entitySource) {
        children = listOf(ParentWithId("parent", entitySource) {
          children = listOf(
            ChildWithId("child.1", entitySource),
            ChildWithId("child.2", entitySource)
          )
        })
      }
    }

    // Second WSM update
    workspaceModel = workspaceModel.update { projectBuilder ->
      projectBuilder.replaceBySource({ true }, storage)
    }

    // Noop WSM update
    workspaceModel = workspaceModel.update { projectBuilder ->
      projectBuilder.replaceBySource({ true }, storage)
    }
  }

  private fun ImmutableEntityStorage.update(updater: (MutableEntityStorage) -> Unit): ImmutableEntityStorage {
    val builder = toBuilder()
    updater(builder)
    return builder.toSnapshot()
  }
}