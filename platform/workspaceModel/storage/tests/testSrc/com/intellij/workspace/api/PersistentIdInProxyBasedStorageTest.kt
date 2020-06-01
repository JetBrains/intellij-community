package com.intellij.workspace.api

import org.junit.Assert.*
import org.junit.Test

data class EntityId(val name: String) : PersistentEntityId<ModuleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal interface EntityWithPersistentId : TypedEntityWithPersistentId {
  val name: String

  @JvmDefault
  override fun persistentId(): EntityId = EntityId(name)
}

private interface EntityWithIdAsField : TypedEntity {
  val entityId: EntityId
  val property: String
}

private interface EntityWithIdAsFieldInInnerClass : TypedEntity {
  val propertyWithId: IdAsFieldClass
  val property: String
}

private interface EntityWithIdsAsList : TypedEntity {
  val propertyWithId: List<EntityId>
}

private interface EntityWithIdAsListInInnerClass : TypedEntity {
  val propertyWithId: IdInListClass
}

data class IdAsFieldClass(val id: EntityId)
data class IdInListClass(val idAsFieldClass: IdAsFieldClass, val list: List<EntityId>)

private interface ModifiableEntityWithPersistentId : EntityWithPersistentId, ModifiableTypedEntity<EntityWithPersistentId> {
  override var name: String
}

private interface ModifiableEntityWithIdAsField : EntityWithIdAsField, ModifiableTypedEntity<EntityWithIdAsField> {
  override var entityId: EntityId
  override var property: String
}

private interface ModifiableEntityWithIdAsFieldInInnerClass : EntityWithIdAsFieldInInnerClass, ModifiableTypedEntity<EntityWithIdAsFieldInInnerClass> {
  override var propertyWithId: IdAsFieldClass
  override var property: String
}

private interface ModifiableEntityWithIdsAsList : EntityWithIdsAsList, ModifiableTypedEntity<EntityWithIdsAsList> {
  override var propertyWithId: List<EntityId>
}

private interface ModifiableEntityWithIdAsListInInnerClass : EntityWithIdAsListInInnerClass, ModifiableTypedEntity<EntityWithIdAsListInInnerClass> {
  override var propertyWithId: IdInListClass
}

private fun TypedEntityStorageBuilder.addEntityWithPersistentId(name: String) =
  addEntity(ModifiableEntityWithPersistentId::class.java, SampleEntitySource("test")) {
    this.name = name
  }

private fun TypedEntityStorageBuilder.addEntityWithIdAsField(entityId: EntityId,
                                                             property: String) =
  addEntity(ModifiableEntityWithIdAsField::class.java, SampleEntitySource("test")) {
    this.entityId = entityId
    this.property = property
  }

private fun TypedEntityStorageBuilder.addEntityWithIdAsFieldInInnerClass(propertyWithId: IdAsFieldClass, property: String) =
  addEntity(ModifiableEntityWithIdAsFieldInInnerClass::class.java, SampleEntitySource("test")) {
    this.propertyWithId = propertyWithId
    this.property = property
  }

private fun TypedEntityStorageBuilder.addEntityWithIdsAsList(propertyWithId: List<EntityId>) =
  addEntity(ModifiableEntityWithIdsAsList::class.java, SampleEntitySource("test")) {
    this.propertyWithId = propertyWithId
  }

private fun TypedEntityStorageBuilder.addEntityWithIdAsListInInnerClass(propertyWithId: IdInListClass) =
  addEntity(ModifiableEntityWithIdAsListInInnerClass::class.java, SampleEntitySource("test")) {
    this.propertyWithId = propertyWithId
  }

class PersistentIdInProxyBasedStorageTest {
  @Test
  fun `rename persistent Id reference as root field`() {
    val newName = "ant"
    val builder = TypedEntityStorageBuilder.createProxy()
    val maven = builder.addEntityWithPersistentId("maven")
    val originSecondEntity = builder.addEntityWithIdAsField(maven.persistentId(), "gradle")
    builder.checkConsistency()
    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, maven) {
      name = newName
    }
    builder.checkConsistency()
    val newSecondEntity = builder.entities(EntityWithIdAsField::class.java).single()
    assertFalse(newSecondEntity === originSecondEntity)
    assertEquals(newSecondEntity.entityId.name, newName)
  }

  @Test
  fun `rename persistent Id reference as field in inner class`() {
    val newName = "ant"
    val builder = TypedEntityStorageBuilder.createProxy()
    val maven = builder.addEntityWithPersistentId("maven")
    val originThirdEntity = builder.addEntityWithIdAsFieldInInnerClass(IdAsFieldClass(maven.persistentId()), "gradle")
    builder.checkConsistency()
    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, maven) {
      name = newName
    }
    builder.checkConsistency()
    val newThirdEntity = builder.entities(EntityWithIdAsFieldInInnerClass::class.java).single()
    assertFalse(newThirdEntity === originThirdEntity)
    assertEquals(newThirdEntity.propertyWithId.id.name, newName)
  }

  @Test
  fun `rename persistent Id reference as list`() {
    val newName = "ant"
    val builder = TypedEntityStorageBuilder.createProxy()
    val maven = builder.addEntityWithPersistentId("maven")
    val gradle = builder.addEntityWithPersistentId("gradle")
    val originList = mutableListOf(maven.persistentId(), gradle.persistentId())
    builder.addEntityWithIdsAsList(originList)
    builder.checkConsistency()
    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, maven) {
      name = newName
    }
    builder.checkConsistency()
    val newList = builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId
    assertFalse(originList === newList)
    newList.first().apply { assertEquals(newName, name) }
  }

  @Test
  fun `rename persistent Id reference in all entities`() {
    val newName = "gant"
    val builder = TypedEntityStorageBuilder.createProxy()
    val ant = builder.addEntityWithPersistentId("ant")
    val maven = builder.addEntityWithPersistentId("maven")
    val gradle = builder.addEntityWithPersistentId("gradle")
    val originList = mutableListOf(ant.persistentId(), gradle.persistentId())
    val originListTwo = mutableListOf(maven.persistentId(), ant.persistentId())
    builder.addEntityWithIdsAsList(originList)
    builder.addEntityWithIdAsListInInnerClass(IdInListClass(IdAsFieldClass(ant.persistentId()), originListTwo))
    builder.checkConsistency()
    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, ant) {
      name = newName
    }
    builder.checkConsistency()
    val newList = builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId
    assertFalse(originList === newList)
    newList.first().apply { assertEquals(newName, name) }

    val dummyClass1 = builder.entities(EntityWithIdAsListInInnerClass::class.java).single().propertyWithId
    dummyClass1.list.last().apply { assertEquals(newName, name) }
    assertEquals(dummyClass1.idAsFieldClass.id.name, newName)
  }

  @Test
  fun `replace property and rename persistent Id reference in all entities`() {
    val gantName = "gant"
    val bazelName = "bazel"
    val builder = TypedEntityStorageBuilder.createProxy()

    val ant = builder.addEntityWithPersistentId("ant")
    val maven = builder.addEntityWithPersistentId("maven")

    val entityMavenIdAsField = builder.addEntityWithIdAsField(maven.persistentId(), "maven")
    builder.addEntityWithIdAsField(ant.persistentId(), "ant")
    val originList = listOf(maven.persistentId())
    builder.addEntityWithIdsAsList(originList)
    builder.checkConsistency()

    builder.modifyEntity(ModifiableEntityWithIdAsField::class.java, entityMavenIdAsField) {
      entityId = ant.persistentId()
    }
    builder.checkConsistency()

    builder.entities(EntityWithIdAsField::class.java).forEach { assertEquals(ant.persistentId(), it.entityId) }
    builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId.forEach { assertEquals(maven.persistentId(), it) }

    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, maven) {
      name = gantName
    }
    builder.checkConsistency()

    builder.entities(EntityWithIdAsField::class.java).forEach { assertEquals(ant.persistentId(), it.entityId) }
    builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId.forEach { assertEquals(gantName, it.name) }

    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, ant) {
      name = bazelName
    }
    builder.checkConsistency()
    builder.entities(EntityWithIdAsField::class.java).forEach { assertEquals(bazelName, it.entityId.name) }
  }

  @Test
  fun `remove id from list and rename persistent Id reference in all entities`() {
    val newName = "gant"
    val builder = TypedEntityStorageBuilder.createProxy()

    val ant = builder.addEntityWithPersistentId("ant")
    val maven = builder.addEntityWithPersistentId("maven")

    builder.addEntityWithIdAsField(maven.persistentId(), "gradle")
    val originList = listOf(ant.persistentId(), maven.persistentId())
    val entityIdsAsList = builder.addEntityWithIdsAsList(originList)
    builder.checkConsistency()

    builder.modifyEntity(ModifiableEntityWithIdsAsList::class.java, entityIdsAsList) {
      propertyWithId = listOf(maven.persistentId())
    }
    builder.checkConsistency()

    var newListWithIds = builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId
    assertEquals(1, newListWithIds.size)
    assertEquals(maven.persistentId(), newListWithIds[0])

    builder.modifyEntity(ModifiableEntityWithPersistentId::class.java, maven) {
      name = newName
    }
    builder.checkConsistency()

    newListWithIds = builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId
    assertEquals(1, newListWithIds.size)
    assertEquals(newName, newListWithIds[0].name)
    assertEquals(newName, builder.entities(EntityWithIdAsField::class.java).single().entityId.name)
  }

  @Test
  fun `remove id from list and check consistency`() {
    val builder = TypedEntityStorageBuilder.createProxy()

    val ant = builder.addEntityWithPersistentId("ant")
    val maven = builder.addEntityWithPersistentId("maven")

    builder.addEntityWithIdAsField(maven.persistentId(), "gradle")
    val originList = listOf(ant.persistentId(), maven.persistentId())
    val entityIdsAsList = builder.addEntityWithIdsAsList(originList)
    builder.checkConsistency()

    builder.modifyEntity(ModifiableEntityWithIdsAsList::class.java, entityIdsAsList) {
      propertyWithId = listOf(ant.persistentId())
    }
    builder.checkConsistency()

    val newListWithIds = builder.entities(EntityWithIdsAsList::class.java).single().propertyWithId
    assertEquals(1, newListWithIds.size)
    assertEquals(ant.persistentId(), newListWithIds[0])
  }
}