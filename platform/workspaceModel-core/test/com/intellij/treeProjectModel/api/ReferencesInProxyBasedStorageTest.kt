package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Test

internal interface ChildEntity : TypedEntity {
  val parent: ParentEntity
  val childProperty: String
  val dataClass: DataClass?
}

internal interface ChildChildEntity : TypedEntity {
  val parent1: ParentEntity
  val parent2: ChildEntity
}

internal interface ParentEntity : ReferableTypedEntity {
  val parentProperty: String

  @JvmDefault
  val children
    get() = referrers(ChildEntity::parent)
}

internal data class DataClass(val stringProperty: String, val parent: EntityReference<ParentEntity>)

private interface ModifiableChildEntity : ChildEntity, ModifiableTypedEntity<ChildEntity> {
  override var parent: ParentEntity
  override var childProperty: String
  override var dataClass: DataClass?
}

private interface ModifiableChildChildEntity : ChildChildEntity, ModifiableTypedEntity<ChildChildEntity> {
  override var parent1: ParentEntity
  override var parent2: ChildEntity
}

private interface ModifiableParentEntity : ParentEntity, ModifiableTypedEntity<ParentEntity> {
  override var parentProperty: String
}

private fun TypedEntityStorageBuilder.addParentEntity(parentProperty: String = "parent") =
  addEntity(ModifiableParentEntity::class.java, SampleEntitySource("test")) {
    this.parentProperty = parentProperty
  }

private fun TypedEntityStorageBuilder.addChildEntity(parentEntity: ParentEntity = addParentEntity(),
                                                     childProperty: String = "child",
                                                     dataClass: DataClass? = null) =
  addEntity(ModifiableChildEntity::class.java, SampleEntitySource("test")) {
    this.parent = parentEntity
    this.childProperty = childProperty
    this.dataClass = dataClass
  }

private fun TypedEntityStorageBuilder.addChildChildEntity(parent1: ParentEntity, parent2: ChildEntity) =
  addEntity(ModifiableChildChildEntity::class.java, SampleEntitySource("test")) {
    this.parent1 = parent1
    this.parent2 = parent2
  }

private fun TypedEntityStorage.singleParent() = entities(ParentEntity::class).single()
private fun TypedEntityStorage.singleChild() = entities(ChildEntity::class).single()

class ReferencesInProxyBasedStorageTest {
  @Test
  fun `add entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity(builder.addParentEntity("foo"))
    builder.checkConsistency()
    assertEquals("foo", child.parent.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parent, builder.singleParent())
    assertEquals(child, child.parent.children.single())
  }

  @Test
  fun `add entity via diff`() {
    val builder = TypedEntityStorageBuilder.create()
    val parentEntity = builder.addParentEntity("foo")

    val diff = TypedEntityStorageBuilder.from(builder.toStorage())
    diff.addChildEntity(parentEntity = parentEntity)
    builder.addDiff(diff)
    builder.checkConsistency()

    val child = builder.singleChild()
    assertEquals("foo", child.parent.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parent, builder.singleParent())
    assertEquals(child, child.parent.children.single())
  }

  @Test
  fun `add remove reference inside data class`() {
    val builder = TypedEntityStorageBuilder.create()
    val parent1 = builder.addParentEntity("parent1")
    val parent2 = builder.addParentEntity("parent2")
    builder.checkConsistency()
    val child = builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(parent2)))
    builder.checkConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList<ChildEntity>(), parent2.children.toList())
    assertEquals("parent1", child.parent.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder).parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(ParentEntity::class).toSet())

    builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      dataClass = null
    }
    builder.checkConsistency()
    assertEquals(setOf(parent1, parent2), builder.entities(ParentEntity::class).toSet())
  }

  @Test
  fun `remove child entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val parent = builder.addParentEntity()
    builder.checkConsistency()
    val child = builder.addChildEntity(parent)
    builder.checkConsistency()
    builder.removeEntity(child)
    builder.checkConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class).toList())
    assertEquals(emptyList<ChildEntity>(), parent.children.toList())
    assertEquals(parent, builder.singleParent())
  }

  @Test
  fun `remove parent entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity()
    builder.removeEntity(child.parent)
    builder.checkConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class).toList())
  }

  @Test
  fun `remove parent entity with two children`() {
    val builder = TypedEntityStorageBuilder.create()
    val child1 = builder.addChildEntity()
    builder.addChildEntity(parentEntity = child1.parent)
    builder.removeEntity(child1.parent)
    builder.checkConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class).toList())
  }

  @Test
  fun `remove parent entity in DAG`() {
    val builder = TypedEntityStorageBuilder.create()
    val parent = builder.addParentEntity()
    val child = builder.addChildEntity(parentEntity = parent)
    builder.addChildChildEntity(parent, child)
    builder.removeEntity(parent)
    builder.checkConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class).toList())
  }

  @Test
  fun `remove parent entity referenced via data class`() {
    val builder = TypedEntityStorageBuilder.create()
    val parent1 = builder.addParentEntity("parent1")
    val parent2 = builder.addParentEntity("parent2")
    builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(parent2)))
    builder.removeEntity(parent2)
    builder.checkConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class).toList())
    assertEquals(listOf(parent1), builder.entities(ParentEntity::class).toList())
    assertEquals(emptyList<ChildEntity>(), parent1.children.toList())
  }

  @Test
  fun `remove parent entity referenced via two paths`() {
    val builder = TypedEntityStorageBuilder.create()
    val parent = builder.addParentEntity()
    builder.addChildEntity(parent, "child", DataClass("data", builder.createReference(parent)))
    builder.checkConsistency()
    builder.removeEntity(parent)
    builder.checkConsistency()
    assertEquals(emptyList<ChildEntity>(), builder.entities(ChildEntity::class).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class).toList())
  }

  @Test
  fun `modify parent property`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity()
    val oldParent = child.parent
    val newParent = builder.modifyEntity(ModifiableParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.checkConsistency()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parent)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify parent property via diff`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity()
    val oldParent = child.parent

    val diff = TypedEntityStorageBuilder.from(builder)
    diff.modifyEntity(ModifiableParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.addDiff(diff)
    builder.checkConsistency()
    val newParent = builder.singleParent()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parent)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify child property`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity()
    val oldParent = child.parent
    val newChild = builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      childProperty = "changed"
    }
    builder.checkConsistency()
    assertEquals("changed", newChild.childProperty)
    assertEquals(oldParent, builder.singleParent())
    assertEquals(newChild, builder.singleChild())
    assertEquals(oldParent, newChild.parent)
    assertEquals(oldParent, child.parent)
    assertEquals(newChild, oldParent.children.single())
    assertEquals("child", child.childProperty)
  }

  @Test
  fun `modify reference to parent`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity()
    val oldParent = child.parent
    val newParent = builder.addParentEntity("new")
    val newChild = builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      parent = newParent
    }
    builder.checkConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals(setOf(oldParent, newParent), builder.entities(ParentEntity::class).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.parent)
    assertEquals(newChild, newParent.children.single())
    assertEquals(oldParent, child.parent)
    assertEquals(emptyList<ChildEntity>(), oldParent.children.toList())
  }

  @Test
  fun `modify reference to parent via data class`() {
    val builder = TypedEntityStorageBuilder.create()
    val parent1 = builder.addParentEntity("parent1")
    val oldParent = builder.addParentEntity("parent2")
    val child = builder.addChildEntity(parent1, "child", DataClass("data", builder.createReference(oldParent)))
    val newParent = builder.addParentEntity("new")
    builder.checkConsistency()
    val newChild = builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      dataClass = DataClass("data2", builder.createReference(newParent))
    }
    builder.checkConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(ParentEntity::class).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.dataClass!!.parent.resolve(builder))
    assertEquals(oldParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `builder from storage`() {
    val storage = TypedEntityStorageBuilder.create().apply {
      addChildEntity()
    }.toStorage()
    storage.checkConsistency()

    assertEquals("parent", storage.singleParent().parentProperty)

    val builder = TypedEntityStorageBuilder.from(storage)
    builder.checkConsistency()

    val oldParent = builder.singleParent()
    assertEquals("parent", oldParent.parentProperty)
    val newParent = builder.modifyEntity(ModifiableParentEntity::class.java, oldParent) {
      parentProperty = "changed"
    }
    builder.checkConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(newParent, builder.singleChild().parent)
    assertEquals("changed", builder.singleChild().parent.parentProperty)
    assertEquals("parent", storage.singleChild().parent.parentProperty)

    val parent2 = builder.addParentEntity("parent2")
    builder.modifyEntity(ModifiableChildEntity::class.java, builder.singleChild()) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    builder.checkConsistency()
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(null, storage.singleChild().dataClass)
    assertEquals("data", builder.singleChild().dataClass!!.stringProperty)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
    assertEquals(setOf(parent2, newParent), builder.entities(ParentEntity::class).toSet())
  }

  @Test
  fun `storage from builder`() {
    val builder = TypedEntityStorageBuilder.create()
    val child = builder.addChildEntity()

    val snapshot = builder.toStorage()
    snapshot.checkConsistency()

    builder.modifyEntity(ModifiableParentEntity::class.java, child.parent) {
      parentProperty = "changed"
    }
    builder.checkConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("changed", builder.singleChild().parent.parentProperty)
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals("parent", snapshot.singleChild().parent.parentProperty)

    val parent2 = builder.addParentEntity("new")
    builder.modifyEntity(ModifiableChildEntity::class.java, child) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    builder.checkConsistency()
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals(null, snapshot.singleChild().dataClass)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
  }
}