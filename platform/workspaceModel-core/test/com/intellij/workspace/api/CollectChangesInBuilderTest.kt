package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectChangesInBuilderTest {
  private lateinit var initialStorage: TypedEntityStorage
  private lateinit var builder : TypedEntityStorageBuilder

  @Before
  fun setUp() {
    initialStorage = TypedEntityStorageBuilder.create().apply {
      addSampleEntity("initial")
      addEntity(SelfModifiableSampleEntity::class.java, SampleEntitySource("test")) {
        intProperty = 1
      }
    }.toStorage()
    builder = TypedEntityStorageBuilder.from(initialStorage)
  }

  @Test
  fun `add remove entity`() {
    builder.addSampleEntity("added")
    builder.addEntity(SelfModifiableSampleEntity::class.java, SampleEntitySource("test")) {
      intProperty = 2
    }
    builder.removeEntity(initialStorage.singleSampleEntity())
    builder.removeEntity(initialStorage.entities(SelfModifiableSampleEntity::class).single())
    @Suppress("UNCHECKED_CAST") val changes = builder.collectChanges(initialStorage).getValue(SampleEntity::class.java) as List<EntityChange<SampleEntity>>
    assertEquals(2, changes.size)
    val (change1, change2) = changes
    assertEquals("added", (change1 as EntityChange.Added).entity.stringProperty)
    assertEquals("initial", (change2 as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `reset changes`() {
    val baseModificationCount = builder.modificationCount
    builder.addSampleEntity("added")
    assertEquals(1, builder.collectChanges(initialStorage).values.flatten().size)
    assertEquals(baseModificationCount + 1, builder.modificationCount)
    builder.resetChanges()
    assertEquals(baseModificationCount + 2, builder.modificationCount)
    assertEquals(0, builder.collectChanges(initialStorage).values.flatten().size)
  }

  @Test
  fun `modify entity`() {
    builder.modifyEntity(ModifiableSampleEntity::class.java, initialStorage.singleSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(SelfModifiableSampleEntity::class.java, initialStorage.entities(SelfModifiableSampleEntity::class).single()) {
      intProperty = 2
    }
    @Suppress("UNCHECKED_CAST")
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

  @Suppress("UNCHECKED_CAST")
  private fun collectSampleEntityChanges(): List<EntityChange<SampleEntity>> {
    val changes = builder.collectChanges(initialStorage)
    if (changes.isEmpty()) return emptyList()
    return changes.entries.single().value as List<EntityChange<SampleEntity>>
  }
}