package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.entities.PSampleEntitySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal interface ProxyNamedSampleEntity : TypedEntityWithPersistentId {
  val name: String
  val next: ProxySampleEntityId

  @JvmDefault
  override fun persistentId(): ProxySampleEntityId = ProxySampleEntityId(name)
}

internal data class ProxySampleEntityId(val name: String) : PersistentEntityId<ProxyNamedSampleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal interface ModifiableProxyNamedSampleEntity : ModifiableTypedEntity<ProxyNamedSampleEntity>, ProxyNamedSampleEntity {
  override var name: String
  override var next: ProxySampleEntityId
}

internal data class ProxyChildEntityId(val childName: String,
                                       override val parentId: ProxySampleEntityId) : PersistentEntityId<ModifiableChildEntityWithPersistentId>() {
  override val presentableName: String
    get() = childName
}

internal interface ModifiableChildEntityWithPersistentId : ModifiableTypedEntity<ModifiableChildEntityWithPersistentId>, TypedEntityWithPersistentId {
  var parent: ProxyNamedSampleEntity
  var childName: String

  @JvmDefault
  override fun persistentId(): PersistentEntityId<*> = ProxyChildEntityId(childName, parent.persistentId())
}

internal fun TypedEntityStorageBuilder.addProxyNamedEntity(name: String, next: ProxySampleEntityId) =
  addEntity(ModifiableProxyNamedSampleEntity::class.java, PSampleEntitySource("test")) {
    this.name = name
    this.next = next
  }

class EntityWithPersistentIdInProxyBasedStorageTest {
  @Test
  fun `add remove entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo = builder.addProxyNamedEntity("foo", ProxySampleEntityId("bar"))
    builder.checkConsistency()
    assertNull(foo.next.resolve(builder))
    val bar = builder.addProxyNamedEntity("bar", ProxySampleEntityId("baz"))
    builder.checkConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.removeEntity(bar)
    builder.checkConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo = builder.addProxyNamedEntity("foo", ProxySampleEntityId("bar"))
    val bar = builder.addProxyNamedEntity("bar", ProxySampleEntityId("baz"))
    builder.checkConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyEntity(ModifiableProxyNamedSampleEntity::class.java, bar) {
      name = "baz"
    }
    builder.checkConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val foo = builder.addProxyNamedEntity("foo", ProxySampleEntityId("bar"))
    val bar = builder.addProxyNamedEntity("bar", ProxySampleEntityId("baz"))
    val baz = builder.addProxyNamedEntity("baz", ProxySampleEntityId("foo"))
    builder.checkConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyEntity(ModifiableProxyNamedSampleEntity::class.java, foo) {
      next = ProxySampleEntityId("baz")
    }
    builder.checkConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val parent = builder.addProxyNamedEntity("parent", ProxySampleEntityId("no"))
    builder.addEntity(ModifiableChildEntityWithPersistentId::class.java, SampleEntitySource("foo")) {
      this.childName = "child"
      this.parent = parent
    }
    builder.checkConsistency()
    builder.removeEntity(parent)
    assertEquals(emptyList<ModifiableChildEntityWithPersistentId>(),
                 builder.entities(ModifiableChildEntityWithPersistentId::class.java).toList())
  }
}