// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.codegen.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.jetbrains.deft.IntellijWsTestIj.modifyEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Soft reference
 * Persistent id via soft reference
 * Persistent id via strong reference
 */
class SoftLinksTest {
  @Test
  fun `test add diff with soft links`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = createEmptyBuilder()
    builder.addEntity(WithSoftLinkEntity {
      this.link = NameId(id)
      this.entitySource = MySource
    })
    builder.addEntity(NamedEntity {
      myName = id
      this.entitySource = MySource
      children = emptyList()
    })

    // Change persistent id in a different builder
    val newBuilder = createBuilderFrom(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())
  }

  @Test
  fun `test add diff with soft links and back`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = createEmptyBuilder()
    builder.addEntity(WithSoftLinkEntity {
      this.link = NameId(id)
      this.entitySource = MySource
    })
    builder.addEntity(NamedEntity {
      myName = id
      this.entitySource = MySource
      children = emptyList()
    })

    // Change persistent id in a different builder
    val newBuilder = createBuilderFrom(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(entity) {
      this.myName = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.referrers(NameId(newId), WithSoftLinkEntity::class.java).toList())

    // Change persistent id to the initial value
    val anotherNewBuilder = createBuilderFrom(builder.toStorage())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyEntity(anotherEntity) {
      this.myName = id
    }

    // Apply changes
    builder.addDiff(anotherNewBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(id)))
    assertOneElement(builder.referrers(NameId(id), WithSoftLinkEntity::class.java).toList())
  }

  @Test
  fun `change persistent id part`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addWithSoftLinkEntity(entity.persistentId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    assertEquals("newName", builder.entities(WithSoftLinkEntity::class.java).single().link.presentableName)
  }

  @Test
  fun `change persistent id part of composed id entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    builder.addComposedIdSoftRefEntity("AnotherName", entity.persistentId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    val updatedPersistentId = builder.entities(ComposedIdSoftRefEntity::class.java).single().persistentId
    assertEquals("newName", updatedPersistentId.link.presentableName)
  }

  @Test
  fun `change persistent id part of composed id entity and with linked entity`() {
    val builder = createEmptyBuilder()
    val entity = builder.addNamedEntity("Name")
    val composedIdEntity = builder.addComposedIdSoftRefEntity("AnotherName", entity.persistentId)
    builder.addComposedLinkEntity(composedIdEntity.persistentId)

    builder.modifyEntity(entity) {
      this.myName = "newName"
    }

    (builder as WorkspaceEntityStorageBuilderImpl).assertConsistency()

    val updatedPersistentId = builder.entities(ComposedIdSoftRefEntity::class.java).single().persistentId
    assertEquals("newName", updatedPersistentId.link.presentableName)
    assertEquals("newName", builder.entities(ComposedLinkEntity::class.java).single().link.link.presentableName)
  }
}
