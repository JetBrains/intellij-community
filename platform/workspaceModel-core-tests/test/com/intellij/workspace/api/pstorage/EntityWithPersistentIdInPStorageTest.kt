package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.PersistentEntityId
import com.intellij.workspace.api.SampleEntitySource
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityWithPersistentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class PNamedSampleEntityData : PEntityData<PNamedSampleEntity>() {
  lateinit var name: String
  lateinit var next: PSampleEntityId
  override fun createEntity(snapshot: TypedEntityStorage): PNamedSampleEntity {
    return PNamedSampleEntity(name, next).also { addMetaData(it, snapshot) }
  }
}

internal class PNamedSampleEntity(
  val name: String,
  val next: PSampleEntityId
) : TypedEntityWithPersistentId, PTypedEntity() {

  override fun persistentId(): PSampleEntityId = PSampleEntityId(name)
}

internal data class PSampleEntityId(val name: String) : PersistentEntityId<PNamedSampleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal class ModifiablePNamedSampleEntity : PModifiableTypedEntity<PNamedSampleEntity>() {
  var name: String by EntityDataDelegation()
  var next: PSampleEntityId by EntityDataDelegation()
}

internal data class PChildEntityId(val childName: String,
                                   override val parentId: PSampleEntityId) : PersistentEntityId<PChildWithPersistentIdEntity>() {
  override val presentableName: String
    get() = childName
}

internal class PChildWithPersistentIdEntityData : PEntityData<PChildWithPersistentIdEntity>() {
  lateinit var parent: PNamedSampleEntity
  lateinit var childName: String
  override fun createEntity(snapshot: TypedEntityStorage): PChildWithPersistentIdEntity {
    return PChildWithPersistentIdEntity(parent, childName).also { addMetaData(it, snapshot) }
  }
}

internal class PChildWithPersistentIdEntity(
  val parent: PNamedSampleEntity,
  val childName: String
) : PTypedEntity(), TypedEntityWithPersistentId {
  override fun persistentId(): PersistentEntityId<*> = PChildEntityId(childName, parent.persistentId())
}

internal class ModifiablePChildWithPersistentIdEntity : PModifiableTypedEntity<PChildWithPersistentIdEntity>() {
  var parent: PNamedSampleEntity by EntityDataDelegation()
  var childName: String by EntityDataDelegation()
}

private fun PEntityStorageBuilder.addPNamedEntity(name: String, next: PSampleEntityId) =
  addEntity(ModifiablePNamedSampleEntity::class.java, SampleEntitySource("test")) {
    this.name = name
    this.next = next
  }

class EntityWithPersistentIdInProxyBasedStorageTest {
  @Test
  fun `add remove entity`() {
    val builder = PEntityStorageBuilder.create()
    val foo = builder.addPNamedEntity("foo", PSampleEntityId("bar"))
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
    val bar = builder.addPNamedEntity("bar", PSampleEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.removeEntity(bar)
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change target entity name`() {
    val builder = PEntityStorageBuilder.create()
    val foo = builder.addPNamedEntity("foo", PSampleEntityId("bar"))
    val bar = builder.addPNamedEntity("bar", PSampleEntityId("baz"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    builder.modifyEntity(ModifiablePNamedSampleEntity::class.java, bar) {
      name = "baz"
    }
    builder.assertConsistency()
    assertNull(foo.next.resolve(builder))
  }

  @Test
  fun `change name in reference`() {
    val builder = PEntityStorageBuilder.create()
    val foo = builder.addPNamedEntity("foo", PSampleEntityId("bar"))
    val bar = builder.addPNamedEntity("bar", PSampleEntityId("baz"))
    val baz = builder.addPNamedEntity("baz", PSampleEntityId("foo"))
    builder.assertConsistency()
    assertEquals(bar, foo.next.resolve(builder))
    val newFoo = builder.modifyEntity(ModifiablePNamedSampleEntity::class.java, foo) {
      next = PSampleEntityId("baz")
    }
    builder.assertConsistency()
    assertEquals(baz, newFoo.next.resolve(builder))
  }

  @Test
  fun `remove child entity with parent entity`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPNamedEntity("parent", PSampleEntityId("no"))
    builder.addEntity(ModifiablePChildWithPersistentIdEntity::class.java, SampleEntitySource("foo")) {
      this.childName = "child"
      this.parent = parent
    }
    builder.assertConsistency()
    builder.removeEntity(parent)
    assertEquals(emptyList<ModifiablePChildWithPersistentIdEntity>(),
                 builder.entities(ModifiablePChildWithPersistentIdEntity::class.java).toList())
  }
}