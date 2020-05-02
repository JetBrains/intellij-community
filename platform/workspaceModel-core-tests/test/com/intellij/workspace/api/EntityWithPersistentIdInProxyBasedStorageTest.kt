package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal interface NamedSampleEntity : TypedEntityWithPersistentId {
  val name: String
  val next: SampleEntityId

  @JvmDefault
  override fun persistentId(): SampleEntityId = SampleEntityId(name)
}

internal data class SampleEntityId(val name: String) : PersistentEntityId<NamedSampleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal interface ModifiableNamedSampleEntity : ModifiableTypedEntity<NamedSampleEntity>, NamedSampleEntity {
  override var name: String
  override var next: SampleEntityId
}

internal data class ChildEntityId(val childName: String, override val parentId: SampleEntityId) : PersistentEntityId<ModifiableChildEntityWithPersistentId>() {
  override val presentableName: String
    get() = childName
}

internal interface ModifiableChildEntityWithPersistentId : ModifiableTypedEntity<ModifiableChildEntityWithPersistentId>, TypedEntityWithPersistentId {
  var parent: NamedSampleEntity
  var childName: String

  @JvmDefault
  override fun persistentId(): PersistentEntityId<*> = ChildEntityId(childName, parent.persistentId())
}

private fun TypedEntityStorageBuilder.addNamedEntity(name: String, next: SampleEntityId) =
  addEntity(ModifiableNamedSampleEntity::class.java, SampleEntitySource("test")) {
    this.name = name
    this.next = next
  }

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