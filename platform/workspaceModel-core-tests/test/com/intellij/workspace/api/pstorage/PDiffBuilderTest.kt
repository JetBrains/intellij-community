package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.pstorage.references.ManyToOne
import com.intellij.workspace.api.pstorage.references.MutableManyToOne
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class PChildSampleEntityData : PEntityData<PChildSampleEntity>() {
  lateinit var data: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildSampleEntity {
    return PChildSampleEntity(data).also { addMetaData(it, snapshot) }
  }
}

internal class PChildSampleEntity(
  val data: String
) : PTypedEntity() {
  val parent: PSampleEntity? by ManyToOne.HardRef.Nullable(PSampleEntity::class)
}

internal class ModifiablePChildSampleEntity : PModifiableTypedEntity<PChildSampleEntity>() {
  var data: String by EntityDataDelegation()
  var parent: PSampleEntity? by MutableManyToOne.HardRef.Nullable(PChildSampleEntity::class, PSampleEntity::class)
}

internal fun TypedEntityStorageBuilder.addPChildSampleEntity(stringProperty: String,
                                                             parent: PSampleEntity?,
                                                             source: EntitySource = PSampleEntitySource("test")): PChildSampleEntity {
  return addEntity(ModifiablePChildSampleEntity::class.java, source) {
    this.data = stringProperty
    this.parent = parent
  }
}

private fun TypedEntityStorageBuilder.applyDiff(anotherBuilder: TypedEntityStorageBuilder): TypedEntityStorage {
  val builder = PEntityStorageBuilder.from(this)
  builder.addDiff(anotherBuilder)
  val storage =  builder.toStorage()
  storage.assertConsistency()
  return storage
}

class ProxyBasedDiffBuilderTest {
  @Test
  fun `add entity`() {
    val source = PEntityStorageBuilder.create()
    source.addPSampleEntity("first")
    val target = PEntityStorageBuilder.create()
    target.addPSampleEntity("second")
    val storage = target.applyDiff(source)
    assertEquals(setOf("first", "second"), storage.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `remove entity`() {
    val target = PEntityStorageBuilder.create()
    val entity = target.addPSampleEntity("hello")
    val entity2 = target.addPSampleEntity("hello")
    val source = PEntityStorageBuilder.from(target.toStorage())
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singlePSampleEntity())
  }

  @Test
  fun `modify entity`() {
    val target = PEntityStorageBuilder.create()
    val entity = target.addPSampleEntity("hello")
    val source = PEntityStorageBuilder.from(target.toStorage())
    source.modifyEntity(ModifiablePSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals("changed", storage.singlePSampleEntity().stringProperty)
  }

  @Test
  fun `remove removed entity`() {
    val target = PEntityStorageBuilder.create()
    val entity = target.addPSampleEntity("hello")
    val entity2 = target.addPSampleEntity("hello")
    val source = PEntityStorageBuilder.from(target.toStorage())
    target.removeEntity(entity)
    target.assertConsistency()
    source.assertConsistency()
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singlePSampleEntity())
  }

  @Test
  fun `modify removed entity`() {
    val target = PEntityStorageBuilder.create()
    val entity = target.addPSampleEntity("hello")
    val source = PEntityStorageBuilder.from(target.toStorage())
    target.removeEntity(entity)
    source.assertConsistency()
    source.modifyEntity(ModifiablePSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals(emptyList<PSampleEntity>(), storage.entities(PSampleEntity::class.java).toList())
  }

  @Test
  fun `remove modified entity`() {
    val target = PEntityStorageBuilder.create()
    val entity = target.addPSampleEntity("hello")
    val source = PEntityStorageBuilder.from(target.toStorage())
    target.modifyEntity(ModifiablePSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity)
    source.assertConsistency()
    val storage = target.applyDiff(source)
    assertEquals(emptyList<PSampleEntity>(), storage.entities(PSampleEntity::class.java).toList())
  }

  @Test
  fun `add entity with refs at the same slot`() {
    val target = PEntityStorageBuilder.create()
    val source = PEntityStorageBuilder.create()
    source.addPSampleEntity("Another entity")
    val parentEntity = target.addPSampleEntity("hello")
    target.addPChildSampleEntity("data", parentEntity)

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(2, resultingStorage.entities(PSampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(PChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(PSampleEntity::class.java).last(), resultingStorage.entities(PChildSampleEntity::class.java).single().parent)
  }

  @Test
  fun `add remove and add with refs`() {
    val source = PEntityStorageBuilder.create()
    val target = PEntityStorageBuilder.create()
    val parent = source.addPSampleEntity("Another entity")
    source.addPChildSampleEntity("String", parent)

    val parentEntity = target.addPSampleEntity("hello")
    target.addPChildSampleEntity("data", parentEntity)

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(2, resultingStorage.entities(PSampleEntity::class.java).toList().size)
    assertEquals(2, resultingStorage.entities(PChildSampleEntity::class.java).toList().size)

    assertNotNull(resultingStorage.entities(PChildSampleEntity::class.java).first().parent)
    assertNotNull(resultingStorage.entities(PChildSampleEntity::class.java).last().parent)

    assertEquals(resultingStorage.entities(PSampleEntity::class.java).first(), resultingStorage.entities(PChildSampleEntity::class.java).first().parent)
    assertEquals(resultingStorage.entities(PSampleEntity::class.java).last(), resultingStorage.entities(PChildSampleEntity::class.java).last().parent)
  }

  @Test
  fun `add dependency without changing entities`() {
    val source = PEntityStorageBuilder.create()
    val parent = source.addPSampleEntity("Another entity")
    source.addPChildSampleEntity("String", null)

    val target = PEntityStorageBuilder.from(source)
    val pchild = target.entities(PChildSampleEntity::class.java).single()
    val pparent = target.entities(PSampleEntity::class.java).single()
    target.modifyEntity(ModifiablePChildSampleEntity::class.java, pchild) {
      this.parent = pparent
    }

    source.addDiff(target)
    source.assertConsistency()

    val resultingStorage = source.toStorage()
    assertEquals(1, resultingStorage.entities(PSampleEntity::class.java).toList().size)
    assertEquals(1, resultingStorage.entities(PChildSampleEntity::class.java).toList().size)

    assertEquals(resultingStorage.entities(PSampleEntity::class.java).single(), resultingStorage.entities(PChildSampleEntity::class.java).single().parent)
  }
}