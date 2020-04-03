package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.*
import org.junit.Assert.assertEquals
import org.junit.Test

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

}