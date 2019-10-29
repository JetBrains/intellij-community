package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Test

private fun TypedEntityStorageBuilder.applyDiff(anotherBuilder: TypedEntityStorageBuilder): TypedEntityStorage {
  val storage = (this as ProxyBasedEntityStorage).applyDiff(anotherBuilder as TypedEntityStorageDiffBuilder)
  storage.checkConsistency()
  return storage
}

class ProxyBasedDiffBuilderTest {
  @Test
  fun `add entity`() {
    val source = TypedEntityStorageBuilder.create()
    source.addSampleEntity("first")
    val target = TypedEntityStorageBuilder.create()
    target.addSampleEntity("second")
    val storage = target.applyDiff(source)
    assertEquals(setOf("first", "second"), storage.entities(SampleEntity::class).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `remove entity`() {
    val target = TypedEntityStorageBuilder.create()
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = TypedEntityStorageBuilder.from(target.toStorage())
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify entity`() {
    val target = TypedEntityStorageBuilder.create()
    val entity = target.addSampleEntity("hello")
    val source = TypedEntityStorageBuilder.from(target.toStorage())
    source.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals("changed", storage.singleSampleEntity().stringProperty)
  }

  @Test
  fun `remove removed entity`() {
    val target = TypedEntityStorageBuilder.create()
    val entity = target.addSampleEntity("hello")
    val entity2 = target.addSampleEntity("hello")
    val source = TypedEntityStorageBuilder.from(target.toStorage())
    target.removeEntity(entity)
    target.checkConsistency()
    source.checkConsistency()
    source.removeEntity(entity)
    val storage = target.applyDiff(source)
    assertEquals(entity2, storage.singleSampleEntity())
  }

  @Test
  fun `modify removed entity`() {
    val target = TypedEntityStorageBuilder.create()
    val entity = target.addSampleEntity("hello")
    val source = TypedEntityStorageBuilder.from(target.toStorage())
    target.removeEntity(entity)
    source.checkConsistency()
    source.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    val storage = target.applyDiff(source)
    assertEquals(emptyList<SampleEntity>(), storage.entities(SampleEntity::class).toList())
  }

  @Test
  fun `remove modified entity`() {
    val target = TypedEntityStorageBuilder.create()
    val entity = target.addSampleEntity("hello")
    val source = TypedEntityStorageBuilder.from(target.toStorage())
    target.modifyEntity(ModifiableSampleEntity::class.java, entity) {
      stringProperty = "changed"
    }
    source.removeEntity(entity)
    source.checkConsistency()
    val storage = target.applyDiff(source)
    assertEquals(emptyList<SampleEntity>(), storage.entities(SampleEntity::class).toList())
  }

}