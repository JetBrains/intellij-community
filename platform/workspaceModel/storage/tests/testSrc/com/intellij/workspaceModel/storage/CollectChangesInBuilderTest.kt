// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class CollectChangesInBuilderTest {
  private lateinit var initialStorage: WorkspaceEntityStorage
  private lateinit var builder: WorkspaceEntityStorageBuilder

  @Before
  fun setUp() {
    initialStorage = createEmptyBuilder().apply {
      addSampleEntity("initial")
      addEntity(ModifiableSecondSampleEntity::class.java, SampleEntitySource("test")) {
        intProperty = 1
      }
    }.toStorage()
    builder = createBuilderFrom(initialStorage)
  }

  @Test
  fun `add remove entity`() {
    builder.addSampleEntity("added")
    builder.addEntity(ModifiableSecondSampleEntity::class.java, SampleEntitySource("test")) {
      intProperty = 2
    }
    builder.removeEntity(initialStorage.singleSampleEntity())
    builder.removeEntity(initialStorage.entities(SecondSampleEntity::class.java).single())
    val changes = builder.collectChanges(initialStorage).getValue(SampleEntity::class.java) as List<EntityChange<SampleEntity>>
    assertEquals(2, changes.size)
    val (change1, change2) = changes
    assertEquals("added", (change1 as EntityChange.Added).entity.stringProperty)
    assertEquals("initial", (change2 as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `modify entity`() {
    builder.modifyEntity(ModifiableSampleEntity::class.java, initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(ModifiableSecondSampleEntity::class.java, initialStorage.entities(SecondSampleEntity::class.java).single()) {
      intProperty = 2
    }
    val change = builder.collectChanges(initialStorage).getValue(SampleEntity::class.java).single() as EntityChange.Replaced<SampleEntity>
    assertEquals("changed", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `modify modified entity`() {
    builder.modifyEntity(ModifiableSampleEntity::class.java, initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(ModifiableSampleEntity::class.java, initialStorage.singleSampleEntity()) {
      stringProperty = "changed again"
    }
    val change = collectSampleEntityChanges().single() as EntityChange.Replaced
    assertEquals("changed again", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `remove modified entity`() {
    val modified = builder.modifyEntity(ModifiableSampleEntity::class.java, initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertEquals("initial", (collectSampleEntityChanges().single() as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `remove added entity`() {
    val added = builder.addSampleEntity("added")
    builder.removeEntity(added)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Test
  fun `modify added entity`() {
    val added = builder.addSampleEntity("added")
    builder.modifyEntity(ModifiableSampleEntity::class.java, added) {
      stringProperty = "changed"
    }
    assertEquals("changed", (collectSampleEntityChanges().single() as EntityChange.Added).entity.stringProperty)
  }

  @Test
  fun `removed modified added entity`() {
    val added = builder.addSampleEntity("added")
    val modified = builder.modifyEntity(ModifiableSampleEntity::class.java, added) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Test
  fun `add parent with child`() {
    val parent = builder.addParentEntity("added")
    builder.addChildEntity(parent, "added")
    val changes = builder.collectChanges(initialStorage)
    val childChange = changes.getValue(ChildEntity::class.java).single() as EntityChange.Added<ChildEntity>
    val parentChange = changes.getValue(ParentEntity::class.java).single() as EntityChange.Added<ParentEntity>
    assertEquals("added", childChange.entity.childProperty)
    assertEquals("added", parentChange.entity.parentProperty)
  }

  @Test
  fun `remove parent with child`() {
    val parent = builder.addParentEntity("to remove")
    builder.addChildEntity(parent, "to remove")
    val storage = builder.toStorage()
    val newBuilder = createBuilderFrom(storage)
    newBuilder.removeEntity(parent)
    val changes = newBuilder.collectChanges(storage)
    val childChange = changes.getValue(ChildEntity::class.java).single() as EntityChange.Removed<ChildEntity>
    val parentChange = changes.getValue(ParentEntity::class.java).single() as EntityChange.Removed<ParentEntity>
    assertEquals("to remove", childChange.entity.childProperty)
    assertEquals("to remove", parentChange.entity.parentProperty)
  }

  private fun collectSampleEntityChanges(): List<EntityChange<SampleEntity>> {
    val changes = builder.collectChanges(initialStorage)
    if (changes.isEmpty()) return emptyList()
    return changes.entries.single().value as List<EntityChange<SampleEntity>>
  }
}