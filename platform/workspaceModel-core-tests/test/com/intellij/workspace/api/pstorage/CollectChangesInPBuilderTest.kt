package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SecondSampleEntityData : PEntityData<SecondSampleEntity>() {
  var intProperty: Int = -1
  override fun createEntity(snapshot: TypedEntityStorage): SecondSampleEntity {
    return SecondSampleEntity(intProperty).also { addMetaData(it, snapshot) }
  }
}

internal class SecondSampleEntity(
  val intProperty: Int
) : PTypedEntity()

internal class ModifiableSecondSampleEntity : PModifiableTypedEntity<SecondSampleEntity>() {
  var intProperty: Int by EntityDataDelegation()
}

class CollectChangesInPBuilderTest {
  private lateinit var initialStorage: TypedEntityStorage
  private lateinit var builder: TypedEntityStorageBuilder

  @Before
  fun setUp() {
    initialStorage = PEntityStorageBuilder.create().apply {
      addPSampleEntity("initial")
      addEntity(ModifiableSecondSampleEntity::class.java, SampleEntitySource("test")) {
        intProperty = 1
      }
    }.toStorage()
    builder = PEntityStorageBuilder.from(initialStorage)
  }

  @Test
  fun `add remove entity`() {
    builder.addPSampleEntity("added")
    builder.addEntity(ModifiableSecondSampleEntity::class.java, SampleEntitySource("test")) {
      intProperty = 2
    }
    builder.removeEntity(initialStorage.singlePSampleEntity())
    builder.removeEntity(initialStorage.entities(SecondSampleEntity::class.java).single())
    @Suppress("UNCHECKED_CAST")
    val changes = builder.collectChanges(initialStorage).getValue(PSampleEntity::class.java) as List<EntityChange<PSampleEntity>>
    assertEquals(2, changes.size)
    val (change1, change2) = changes
    assertEquals("added", (change1 as EntityChange.Added).entity.stringProperty)
    assertEquals("initial", (change2 as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `reset changes`() {
    val baseModificationCount = builder.modificationCount
    builder.addPSampleEntity("added")
    assertEquals(1, builder.collectChanges(initialStorage).values.flatten().size)
    assertEquals(baseModificationCount + 1, builder.modificationCount)
    builder.resetChanges()
    assertEquals(baseModificationCount + 2, builder.modificationCount)
    assertEquals(0, builder.collectChanges(initialStorage).values.flatten().size)
  }

  @Test
  fun `modify entity`() {
    builder.modifyEntity(ModifiablePSampleEntity::class.java, initialStorage.singlePSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(ModifiableSecondSampleEntity::class.java, initialStorage.entities(SecondSampleEntity::class.java).single()) {
      intProperty = 2
    }
    @Suppress("UNCHECKED_CAST")
    val change = builder.collectChanges(initialStorage).getValue(PSampleEntity::class.java).single() as EntityChange.Replaced<PSampleEntity>
    assertEquals("changed", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `modify modified entity`() {
    builder.modifyEntity(ModifiablePSampleEntity::class.java, initialStorage.singlePSampleEntity()) {
      stringProperty = "changed"
    }
    builder.modifyEntity(ModifiablePSampleEntity::class.java, initialStorage.singlePSampleEntity()) {
      stringProperty = "changed again"
    }
    val change = collectSampleEntityChanges().single() as EntityChange.Replaced
    assertEquals("changed again", change.newEntity.stringProperty)
    assertEquals("initial", change.oldEntity.stringProperty)
  }

  @Test
  fun `remove modified entity`() {
    val modified = builder.modifyEntity(ModifiablePSampleEntity::class.java, initialStorage.singlePSampleEntity()) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertEquals("initial", (collectSampleEntityChanges().single() as EntityChange.Removed).entity.stringProperty)
  }

  @Test
  fun `remove added entity`() {
    val added = builder.addPSampleEntity("added")
    builder.removeEntity(added)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Test
  fun `modify added entity`() {
    val added = builder.addPSampleEntity("added")
    builder.modifyEntity(ModifiablePSampleEntity::class.java, added) {
      stringProperty = "changed"
    }
    assertEquals("changed", (collectSampleEntityChanges().single() as EntityChange.Added).entity.stringProperty)
  }

  @Test
  fun `removed modified added entity`() {
    val added = builder.addPSampleEntity("added")
    val modified = builder.modifyEntity(ModifiablePSampleEntity::class.java, added) {
      stringProperty = "changed"
    }
    builder.removeEntity(modified)
    assertTrue(collectSampleEntityChanges().isEmpty())
  }

  @Suppress("UNCHECKED_CAST")
  private fun collectSampleEntityChanges(): List<EntityChange<PSampleEntity>> {
    val changes = builder.collectChanges(initialStorage)
    if (changes.isEmpty()) return emptyList()
    return changes.entries.single().value as List<EntityChange<PSampleEntity>>
  }
}