// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

@Suppress("UNCHECKED_CAST")
class CollectChangesInBuilderTest {
  private lateinit var initialStorage: ImmutableEntityStorage
  private lateinit var builder: MutableEntityStorage

  @BeforeEach
  fun setUp() {
    initialStorage = createEmptyBuilder().apply {
      this addEntity SampleEntity(false,
                                  "initial",
                                  ArrayList(),
                                  HashMap(),
                                  VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
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
                                   VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
                                   SampleEntitySource("test"))
    builder.addEntity(SecondSampleEntity(2, SampleEntitySource("test")))
    builder.removeEntity(initialStorage.singleSampleEntity())
    builder.removeEntity(initialStorage.entities(SecondSampleEntity::class.java).single())
    val changes = assertChangelogSize(4).getValue(SampleEntity::class.java) as List<EntityChange<SampleEntity>>
    val change1 = changes.single { it is EntityChange.Added }
    val change2 = changes.single { it is EntityChange.Removed }
    assertEquals("added", (change1 as EntityChange.Added).newEntity.stringProperty)
    assertEquals("initial", (change2 as EntityChange.Removed).oldEntity.stringProperty)
  }

  @Test
  fun `modify entity`() {
    builder.modifySampleEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifySecondSampleEntity(initialStorage.entities(SecondSampleEntity::class.java).single()) {
      intProperty = 2
    }
    val changes = assertChangelogSize(2)
    val change = changes.getValue(SampleEntity::class.java).single() as EntityChange.Replaced<SampleEntity>
    assertEquals("changed", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `modify modified entity`() {
    builder.modifySampleEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifySampleEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed again"
    }
    assertChangelogSize(1)
    val change = collectSampleEntityChanges().single() as EntityChange.Replaced
    assertEquals("changed again", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `remove modified entity`() {
    val modified = builder.modifySampleEntity(initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertChangelogSize(1)
    assertEquals("initial", (collectSampleEntityChanges().single() as EntityChange.Removed).oldEntity.stringProperty)
  }

  @Test
  fun `remove added entity`() {
    val added = builder addEntity SampleEntity(false,
                                               "added",
                                               ArrayList(),
                                               HashMap(),
                                               VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
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
                                               VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    builder.modifySampleEntity(added) {
      stringProperty = "changed"
    }
    assertChangelogSize(1)
    assertEquals("changed", (collectSampleEntityChanges().single() as EntityChange.Added).newEntity.stringProperty)
  }

  @Test
  fun `removed modified added entity`() {
    val added = builder addEntity SampleEntity(false,
                                               "added",
                                               ArrayList(),
                                               HashMap(),
                                               VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
                                               SampleEntitySource("test"))
    val modified = builder.modifySampleEntity(added) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertChangelogSize(0)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Test
  fun `add parent with child`() {
    val parent = XParentEntity("added", MySource)
    builder addEntity XChildEntity("added", MySource) {
      parentEntity = parent
    }
    val changes = assertChangelogSize(2)
    val childChange = changes.getValue(XChildEntity::class.java).single() as EntityChange.Added<XChildEntity>
    val parentChange = changes.getValue(XParentEntity::class.java).single() as EntityChange.Added<XParentEntity>
    assertEquals("added", childChange.newEntity.childProperty)
    assertEquals("added", parentChange.newEntity.parentProperty)
  }

  @Test
  fun `remove parent with child`() {
    val parent = builder addEntity  XParentEntity("to remove", MySource) {
      this.children += XChildEntity("to remove", MySource)
    }
    val storage = builder.toSnapshot()
    val newBuilder = createBuilderFrom(storage)
    newBuilder.removeEntity(parent.from(newBuilder))
    val changes = assertChangelogSize(2, newBuilder, storage)
    val childChange = changes.getValue(XChildEntity::class.java).single() as EntityChange.Removed<XChildEntity>
    val parentChange = changes.getValue(XParentEntity::class.java).single() as EntityChange.Removed<XParentEntity>
    assertEquals("to remove", childChange.oldEntity.childProperty)
    assertEquals("to remove", parentChange.oldEntity.parentProperty)
  }

  @Test
  fun `remove child by modifying parent`() {
    val parent = XParentEntity("parent", MySource)
    builder addEntity XChildEntity("to remove", MySource) {
      parentEntity = parent
    }
    val snapshot = builder.toSnapshot()
    val newBuilder = createBuilderFrom(snapshot)
    newBuilder.modifyXParentEntity(snapshot.entities(XParentEntity::class.java).single()) {
      children = emptyList()
    }
    val changes = assertChangelogSize(2, newBuilder, snapshot)
    val childChanges = changes.getValue(XChildEntity::class.java)
    assertEquals("to remove", (childChanges.single() as EntityChange.Removed<XChildEntity>).oldEntity.childProperty)
  }

  @Test
  fun `move child between parents`() {
    val parent = XParentEntity("One", MySource)
    val parent2 = builder addEntity XParentEntity("Two", MySource)
    val child = builder addEntity XChildEntity("Child", MySource) {
      parentEntity = parent
    }
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder.modifyXParentEntity(parent2.from(newBuilder)) parent@{
      newBuilder.modifyXChildEntity(child.from(newBuilder)) child@{
        this@parent.children = listOf(this@child)
      }
    }

    assertEquals(0, newBuilder.entities(XParentEntity::class.java).single { it.parentProperty == "One" }.children.size)
    assertEquals(1, newBuilder.entities(XParentEntity::class.java).single { it.parentProperty == "Two" }.children.size)
    assertEquals("Two", newBuilder.entities(XChildEntity::class.java).single().parentEntity.parentProperty)

    assertChangelogSize(3, newBuilder, snapshot)
  }

  @Test
  fun `move child between parents from child`() {
    val parent = XParentEntity("One", MySource)
    val parent2 = builder addEntity XParentEntity("Two", MySource)
    val child = builder addEntity XChildEntity("Child", MySource) {
      parentEntity = parent
    }
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder.modifyXChildEntity(child.from(newBuilder)) child@{
      newBuilder.modifyXParentEntity(parent2.from(newBuilder)) parent@{
        this@child.parentEntity = this@parent
      }
    }

    assertEquals(0, newBuilder.entities(XParentEntity::class.java).single { it.parentProperty == "One" }.children.size)
    assertEquals(1, newBuilder.entities(XParentEntity::class.java).single { it.parentProperty == "Two" }.children.size)
    assertEquals("Two", newBuilder.entities(XChildEntity::class.java).single().parentEntity.parentProperty)

    assertChangelogSize(3, newBuilder, snapshot)
  }

  @Test
  fun `create child by modifying parent`() {
    val parent = builder addEntity XParentEntity("One", MySource)
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder.modifyXParentEntity(parent.from(newBuilder)) {
      this.children = listOf(XChildEntity("Child", MySource))
    }

    assertEquals(1, newBuilder.entities(XParentEntity::class.java).single().children.size)
    assertEquals("One", newBuilder.entities(XChildEntity::class.java).single().parentEntity.parentProperty)

    val changes = assertChangelogSize(2, newBuilder, snapshot)
    assertIs<EntityChange.Replaced<*>>(changes[XParentEntity::class.java]?.single())
    assertIs<EntityChange.Added<*>>(changes[XChildEntity::class.java]?.single())
  }

  @Test
  fun `create child by adding child with link to parent`() {
    val parent = builder addEntity XParentEntity("One", MySource)
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder addEntity XChildEntity("Child", MySource) child@{
      newBuilder.modifyXParentEntity(parent.from(newBuilder)) parent@{
        this@child.parentEntity = this@parent
      }
    }

    assertEquals(1, newBuilder.entities(XParentEntity::class.java).single().children.size)
    assertEquals("One", newBuilder.entities(XChildEntity::class.java).single().parentEntity.parentProperty)

    val changes = assertChangelogSize(2, newBuilder, snapshot)
    assertIs<EntityChange.Replaced<*>>(changes[XParentEntity::class.java]?.single())
    assertIs<EntityChange.Added<*>>(changes[XChildEntity::class.java]?.single())
  }

  @Test
  fun `create reference between entities from parent`() {
    val parent = builder addEntity OptionalOneToOneParentEntity(MySource)
    val child = builder addEntity OptionalOneToOneChildEntity("Hey", MySource)
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder.modifyOptionalOneToOneParentEntity(parent.from(newBuilder)) parent@{
      newBuilder.modifyOptionalOneToOneChildEntity(child.from(newBuilder)) child@{
        this@parent.child = this@child
      }
    }

    assertNotNull(newBuilder.entities(OptionalOneToOneParentEntity::class.java).single().child)
    assertNotNull(newBuilder.entities(OptionalOneToOneChildEntity::class.java).single().parent)

    val changes = assertChangelogSize(2, newBuilder, snapshot)
    assertIs<EntityChange.Replaced<*>>(changes[OptionalOneToOneParentEntity::class.java]?.single())
    assertIs<EntityChange.Replaced<*>>(changes[OptionalOneToOneChildEntity::class.java]?.single())
  }

  @Test
  fun `create reference between entities from child`() {
    val parent = builder addEntity OptionalOneToOneParentEntity(MySource)
    val child = builder addEntity OptionalOneToOneChildEntity("Hey", MySource)
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder.modifyOptionalOneToOneChildEntity(child.from(newBuilder)) child@{
      newBuilder.modifyOptionalOneToOneParentEntity(parent.from(newBuilder)) parent@{
        this@child.parent = this@parent
      }
    }

    assertNotNull(newBuilder.entities(OptionalOneToOneParentEntity::class.java).single().child)
    assertNotNull(newBuilder.entities(OptionalOneToOneChildEntity::class.java).single().parent)

    val changes = assertChangelogSize(2, newBuilder, snapshot)
    assertIs<EntityChange.Replaced<*>>(changes[OptionalOneToOneParentEntity::class.java]?.single())
    assertIs<EntityChange.Replaced<*>>(changes[OptionalOneToOneChildEntity::class.java]?.single())
  }

  @Test
  fun `remove child`() {
    val child = builder addEntity OptionalOneToOneChildEntity("Hey", MySource) {
      this.parent = OptionalOneToOneParentEntity(MySource)
    }
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder.removeEntity(child.from(newBuilder))

    assertNull(newBuilder.entities(OptionalOneToOneParentEntity::class.java).single().child)
    assertTrue(newBuilder.entities(OptionalOneToOneChildEntity::class.java).toList().isEmpty())

    val changes = assertChangelogSize(2, newBuilder, snapshot)
    assertIs<EntityChange.Replaced<*>>(changes[OptionalOneToOneParentEntity::class.java]?.single())
    assertIs<EntityChange.Removed<*>>(changes[OptionalOneToOneChildEntity::class.java]?.single())
  }

  @Test
  fun `create parent to the existing child`() {
    val child = builder addEntity OptionalOneToOneChildEntity("Hey", MySource)
    val snapshot = builder.toSnapshot()
    val newBuilder = snapshot.toBuilder()

    newBuilder addEntity OptionalOneToOneParentEntity(MySource) parent@{
      newBuilder.modifyOptionalOneToOneChildEntity(child.from(newBuilder)) child@{
        this@parent.child = this@child
      }
    }

    assertNotNull(newBuilder.entities(OptionalOneToOneParentEntity::class.java).single().child)
    assertNotNull(newBuilder.entities(OptionalOneToOneChildEntity::class.java).single().parent)

    val changes = assertChangelogSize(2, newBuilder, snapshot)
    assertIs<EntityChange.Added<*>>(changes[OptionalOneToOneParentEntity::class.java]?.single())
    assertIs<EntityChange.Replaced<*>>(changes[OptionalOneToOneChildEntity::class.java]?.single())
  }

  private fun assertChangelogSize(size: Int,
                                  myBuilder: MutableEntityStorage = builder,
                                  original: ImmutableEntityStorage = initialStorage): Map<Class<*>, List<EntityChange<*>>> {
    val changes = (myBuilder as MutableEntityStorageInstrumentation).collectChanges()
    assertEquals(size, changes.values.flatten().size)
    return changes
  }

  private fun collectSampleEntityChanges(): List<EntityChange<SampleEntity>> {
    val changes = (builder as MutableEntityStorageInstrumentation).collectChanges()
    if (changes.isEmpty()) return emptyList()
    return changes.entries.single().value as List<EntityChange<SampleEntity>>
  }
}