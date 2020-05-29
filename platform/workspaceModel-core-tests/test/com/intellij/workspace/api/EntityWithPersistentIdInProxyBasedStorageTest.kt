package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.entities.ModifiableChildEntityWithPersistentId
import com.intellij.workspace.api.pstorage.entities.ModifiableNamedSampleEntity
import com.intellij.workspace.api.pstorage.entities.SampleEntityId
import com.intellij.workspace.api.pstorage.entities.addNamedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityWithPersistentIdInProxyBasedStorageTest {
  @Test
  fun `add remove entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo = builder.addNamedEntity("foo", SampleEntityId("bar"))
    builder.checkConsistency()
    assertNull(foo.next.resolve(builder))
    val bar = builder.addNamedEntity("bar", SampleEntityId("baz"))
    builder.checkConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.removeEntity(bar)
    builder.checkConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo = builder.addNamedEntity("foo", SampleEntityId("bar"))
    val bar = builder.addNamedEntity("bar", SampleEntityId("baz"))
    builder.checkConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyEntity(ModifiableNamedSampleEntity::class.java, bar) {
      name = "baz"
    }
    builder.checkConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo = builder.addNamedEntity("foo", SampleEntityId("bar"))
    val bar = builder.addNamedEntity("bar", SampleEntityId("baz"))
    val baz = builder.addNamedEntity("baz", SampleEntityId("foo"))
    builder.checkConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyEntity(ModifiableNamedSampleEntity::class.java, foo) {
      next = SampleEntityId("baz")
    }
    builder.checkConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val parent = builder.addNamedEntity("parent", SampleEntityId("no"))
    builder.addEntity(ModifiableChildEntityWithPersistentId::class.java, SampleEntitySource("foo")) {
      this.childName = "child"
      this.parent = parent
    }
    builder.checkConsistency()
    builder.removeEntity(parent)
    assertEquals(emptyList<ModifiableChildEntityWithPersistentId>(), builder.entities(ModifiableChildEntityWithPersistentId::class.java).toList())
  }
}