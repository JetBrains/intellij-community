// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.*
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import org.junit.Test

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
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    builder.addEntity(ModifiableWithSoftLinkEntity::class.java, MySource) {
      this.link = NameId(id)
    }
    builder.addEntity(ModifiableNamedEntity::class.java, MySource) {
      name = id
    }

    // Change persistent id in a different builder
    val newBuilder = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.indexes.softLinks.getValues(NameId(newId)))
  }

  @Test
  fun `test add diff with soft links and back`() {
    val id = "MyId"
    val newId = "MyNewId"

    // Setup builder for test
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    builder.addEntity(ModifiableWithSoftLinkEntity::class.java, MySource) {
      this.link = NameId(id)
    }
    builder.addEntity(ModifiableNamedEntity::class.java, MySource) {
      name = id
    }

    // Change persistent id in a different builder
    val newBuilder = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val entity = newBuilder.resolve(NameId(id))!!
    newBuilder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = newId
    }

    // Apply changes
    builder.addDiff(newBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(newId)))
    assertOneElement(builder.indexes.softLinks.getValues(NameId(newId)))

    // Change persistent id to the initial value
    val anotherNewBuilder = WorkspaceEntityStorageBuilderImpl.from(builder.toStorage())
    val anotherEntity = anotherNewBuilder.resolve(NameId(newId))!!
    anotherNewBuilder.modifyEntity(ModifiableNamedEntity::class.java, anotherEntity) {
      this.name = id
    }

    // Apply changes
    builder.addDiff(anotherNewBuilder)

    // Check
    assertNotNull(builder.resolve(NameId(id)))
    assertOneElement(builder.indexes.softLinks.getValues(NameId(id)))
  }

  @Test
  fun `change persistent id part`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addNamedEntity("Name")
    builder.addWithSoftLinkEntity(entity.persistentId())

    builder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = "newName"
    }

    builder.assertConsistency()

    assertEquals("newName", builder.entities(WithSoftLinkEntity::class.java).single().link.presentableName)
  }

  @Test
  fun `change persistent id part of composed id entity`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addNamedEntity("Name")
    builder.addComposedIdSoftRefEntity("AnotherName", entity.persistentId())

    builder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = "newName"
    }

    builder.assertConsistency()

    val updatedPersistentId = builder.entities(ComposedIdSoftRefEntity::class.java).single().persistentId()
    assertEquals("newName", updatedPersistentId.link.presentableName)
  }

  @Test
  fun `change persistent id part of composed id entity and with linked entity`() {
    val builder = WorkspaceEntityStorageBuilderImpl.create()
    val entity = builder.addNamedEntity("Name")
    val composedIdEntity = builder.addComposedIdSoftRefEntity("AnotherName", entity.persistentId())
    builder.addWithSoftLinkEntity(composedIdEntity.persistentId())

    builder.modifyEntity(ModifiableNamedEntity::class.java, entity) {
      this.name = "newName"
    }

    builder.assertConsistency()

    val updatedPersistentId = builder.entities(ComposedIdSoftRefEntity::class.java).single().persistentId()
    assertEquals("newName", updatedPersistentId.link.presentableName)
    assertEquals("newName", (builder.entities(WithSoftLinkEntity::class.java).single().link as ComposedId).link.presentableName)
  }
}