// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.platform.workspaceModel.storage.testEntities.entities.*
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.toBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class CollectChangesInBuilderTest {
  private lateinit var initialStorage: EntityStorageSnapshot
  private lateinit var builder: MutableEntityStorage

  @Before
  fun setUp() {
    initialStorage = createEmptyBuilder().apply {
      this addEntity SampleEntity(false,
                                  "initial",
                                  ArrayList(),
                                  HashMap(),
                                  VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                  SampleEntitySource("test"))
      addEntity(SecondSampleEntity(1, SampleEntitySource("test")))
    }.toSnapshot()
    builder = createBuilderFrom(initialStorage)
  }

  @Test
  fun `add remove entity`() {
    builder addEntity SampleEntity(false,
                                   "added",
                                   ArrayList(),
                                   HashMap(),
                                   VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                   SampleEntitySource("test"))
    builder.addEntity(SecondSampleEntity(2, SampleEntitySource("test")))
    builder.removeEntity(initialStorage.singleSampleEntity())
    builder.removeEntity(initialStorage.entities(SecondSampleEntity::class.java).single())
    val changes = assertChangelogSize(4).getValue(SampleEntity::class.java) as Set<EntityChange<SampleEntity>>
    val change1 = changes.single { it is EntityChange.Added }
    val change2 = changes.single { it is EntityChange.Removed }
    assertEquals("added", (change1 as EntityChange.Added).entity.stringProperty)
    assertEquals("initial", (change2 as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `modify entity`() {
    builder.modifyEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(initialStorage.entities(SecondSampleEntity::class.java).single()) {
      intProperty = 2
    }
    val changes = assertChangelogSize(2)
    val change = changes.getValue(SampleEntity::class.java).single() as EntityChange.Replaced<SampleEntity>
    assertEquals("changed", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `modify modified entity`() {
    builder.modifyEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed again"
    }
    assertChangelogSize(1)
    val change = collectSampleEntityChanges().single() as EntityChange.Replaced
    assertEquals("changed again", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `remove modified entity`() {
    val modified = builder.modifyEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertChangelogSize(1)
    assertEquals("initial", (collectSampleEntityChanges().single() as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `remove added entity`() {
    val added = builder addEntity SampleEntity(false,
                                               "added",
                                               ArrayList(),
                                               HashMap(),
                                               VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    builder.removeEntity(added)
    assertChangelogSize(0)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Test
  fun `modify added entity`() {
    val added = builder addEntity SampleEntity(false,
                                               "added",
                                               ArrayList(),
                                               HashMap(),
                                               VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    builder.modifyEntity(added) {
      stringProperty = "changed"
    }
    assertChangelogSize(1)
    assertEquals("changed", (collectSampleEntityChanges().single() as EntityChange.Added).entity.stringProperty)
  }

  @Test
  fun `removed modified added entity`() {
    val added = builder addEntity SampleEntity(false,
                                               "added",
                                               ArrayList(),
                                               HashMap(),
                                               VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val modified = builder.modifyEntity(added) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertChangelogSize(0)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Test
  fun `add parent with child`() {
    val parent = builder addEntity XParentEntity("added", MySource)
    builder addEntity XChildEntity("added", MySource) {
      parentEntity = parent
    }
    val changes = assertChangelogSize(2)
    val childChange = changes.getValue(XChildEntity::class.java).single() as EntityChange.Added<XChildEntity>
    val parentChange = changes.getValue(XParentEntity::class.java).single() as EntityChange.Added<XParentEntity>
    assertEquals("added", childChange.entity.childProperty)
    assertEquals("added", parentChange.entity.parentProperty)
  }

  @Test
  fun `remove parent with child`() {
    val parent = builder addEntity XParentEntity("to remove", MySource)
    builder addEntity XChildEntity("to remove", MySource) {
      parentEntity = parent
    }
    val storage = builder.toSnapshot()
    val newBuilder = createBuilderFrom(storage)
    newBuilder.removeEntity(parent.from(newBuilder))
    val changes = assertChangelogSize(2, newBuilder, storage)
    val childChange = changes.getValue(XChildEntity::class.java).single() as EntityChange.Removed<XChildEntity>
    val parentChange = changes.getValue(XParentEntity::class.java).single() as EntityChange.Removed<XParentEntity>
    assertEquals("to remove", childChange.entity.childProperty)
    assertEquals("to remove", parentChange.entity.parentProperty)
  }

  @Test
  fun `remove child by modifying parent`() {
    val parent = builder addEntity XParentEntity("parent", MySource)
    builder addEntity XChildEntity("to remove", MySource) {
      parentEntity = parent
    }
    val snapshot = builder.toSnapshot()
    val newBuilder = createBuilderFrom(snapshot)
    newBuilder.modifyEntity(snapshot.entities(XParentEntity::class.java).single()) {
      children = emptyList()
    }
    val changes = assertChangelogSize(2, newBuilder, snapshot)
    val childChanges = changes.getValue(XChildEntity::class.java)
    assertEquals("to remove", (childChanges.single() as EntityChange.Removed<XChildEntity>).oldEntity.childProperty)
  }

  @Test
  fun `move child between parents`() {
    val parent = builder addEntity XParentEntity("One", MySource)
    val parent2 = builder addEntity XParentEntity("Two", MySource)
    val child = builder addEntity XChildEntity("Child", MySource) {
      parentEntity = parent
    }
    (builder as MutableEntityStorageImpl).changeLog.clear()
    val snapshot = builder.toSnapshot()

    builder.modifyEntity(parent2) {
      this.children = listOf(child)
    }

    assertEquals(0, builder.entities(XParentEntity::class.java).single { it.parentProperty == "One" }.children.size)
    assertEquals(1, builder.entities(XParentEntity::class.java).single { it.parentProperty == "Two" }.children.size)
    assertEquals("Two", builder.entities(XChildEntity::class.java).single().parentEntity.parentProperty)

    // This should actually generate 2 events. However, current implementation off changes collecting
    //  has some issues that can't be fixed because the platform relays on them
    // See doc for [MutableEntityStorage.collectChanges] for more info
    assertChangelogSize(1)
  }

  @Test
  fun `move child between parents from child`() {
    val parent = builder addEntity XParentEntity("One", MySource)
    val parent2 = builder addEntity XParentEntity("Two", MySource)
    val child = builder addEntity XChildEntity("Child", MySource) {
      parentEntity = parent
    }
    (builder as MutableEntityStorageImpl).changeLog.clear()
    val snapshot = builder.toSnapshot()

    builder.modifyEntity(child) {
      this.parentEntity = parent2
    }

    assertEquals(0, builder.entities(XParentEntity::class.java).single { it.parentProperty == "One" }.children.size)
    assertEquals(1, builder.entities(XParentEntity::class.java).single { it.parentProperty == "Two" }.children.size)
    assertEquals("Two", builder.entities(XChildEntity::class.java).single().parentEntity.parentProperty)

    // This should actually generate 3 events. However, current implementation off changes collecting
    //  has some issues that can't be fixed because the platform relays on them
    // See doc for [MutableEntityStorage.collectChanges] for more info
    assertChangelogSize(1)
  }

  @Test
  fun `collectChanges for identical entities doesnt squash changes`() {
    builder addEntity ParentEntity("data", MySource)
    builder addEntity ParentEntity("data", MySource)

    assertChangelogSize(2)

    val snapshot = builder.toSnapshot()
    val anotherBuilder = snapshot.toBuilder()
    anotherBuilder.entities(ParentEntity::class.java).forEach {
      anotherBuilder.modifyEntity(it) {
        this.parentData = "data2"
      }
    }

    assertChangelogSize(2, anotherBuilder, snapshot)

    val snapshotBeforeRemove = anotherBuilder.toSnapshot()
    val builderForRemove = snapshotBeforeRemove.toBuilder()
    builderForRemove.entities(ParentEntity::class.java).toList().forEach {
      builderForRemove.removeEntity(it)
    }

    assertChangelogSize(2, builderForRemove, snapshotBeforeRemove)
  }

  private fun assertChangelogSize(size: Int,
                                  myBuilder: MutableEntityStorage = builder,
                                  original: EntityStorageSnapshot = initialStorage): Map<Class<*>, Set<EntityChange<*>>> {
    val changes = myBuilder.collectChanges(original)
    assertEquals(size, changes.values.flatten().size)
    return changes
  }

  private fun collectSampleEntityChanges(): Set<EntityChange<SampleEntity>> {
    val changes = builder.collectChanges(initialStorage)
    if (changes.isEmpty()) return emptySet()
    return changes.entries.single().value as Set<EntityChange<SampleEntity>>
  }
}