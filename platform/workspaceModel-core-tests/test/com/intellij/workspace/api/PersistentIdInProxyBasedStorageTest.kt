package com.intellij.workspace.api

import org.junit.Assert.*
import org.junit.Test

data class FirstEntityId(val name: String) : PersistentEntityId<ModuleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

internal interface FirstEntity : TypedEntityWithPersistentId {
  val name: String
  @JvmDefault
  override fun persistentId(): FirstEntityId = FirstEntityId(name)
}

internal interface SecondEntity : TypedEntity {
  val firstId: FirstEntityId
  val property1: String
}

internal interface ThirdEntity : TypedEntity {
  val property1: DummyClass
  val property2: String
}

internal interface FourthEntity : TypedEntity {
  val property1: List<FirstEntityId>
}

internal interface FifthEntity : TypedEntity {
  val property1: DummyClass1
}

data class DummyClass(val firstId: FirstEntityId)
data class DummyClass1(val dummyClass: DummyClass, val list: List<FirstEntityId>)

private interface ModifiableFirstEntity : FirstEntity, ModifiableTypedEntity<FirstEntity> {
  override var name: String
}

private interface ModifiableSecondEntity : SecondEntity, ModifiableTypedEntity<SecondEntity> {
  override var firstId: FirstEntityId
  override var property1: String
}

private interface ModifiableThirdEntity : ThirdEntity, ModifiableTypedEntity<ThirdEntity> {
  override var property1: DummyClass
  override var property2: String
}

private interface ModifiableFourthEntity : FourthEntity, ModifiableTypedEntity<FourthEntity> {
  override var property1: List<FirstEntityId>
}

private interface ModifiableFifthEntity : FifthEntity, ModifiableTypedEntity<FifthEntity> {
  override var property1: DummyClass1
}

private fun TypedEntityStorageBuilder.addFirstEntity(name: String) =
  addEntity(ModifiableFirstEntity::class.java, SampleEntitySource("test")) {
    this.name = name
  }

private fun TypedEntityStorageBuilder.addSecondEntity(persistentId: FirstEntityId,
                                                      property1: String) =
  addEntity(ModifiableSecondEntity::class.java, SampleEntitySource("test")) {
    this.firstId = persistentId
    this.property1 = property1
  }

private fun TypedEntityStorageBuilder.addThirdEntity(property1: DummyClass, property2: String) =
  addEntity(ModifiableThirdEntity::class.java, SampleEntitySource("test")) {
    this.property1 = property1
    this.property2 = property2
  }

private fun TypedEntityStorageBuilder.addFourthEntity(property1: List<FirstEntityId>) =
  addEntity(ModifiableFourthEntity::class.java, SampleEntitySource("test")) {
    this.property1 = property1
  }

private fun TypedEntityStorageBuilder.addFifthEntity(property1: DummyClass1) =
  addEntity(ModifiableFifthEntity::class.java, SampleEntitySource("test")) {
    this.property1 = property1
  }

class PersistentIdInProxyBasedStorageTest {
  @Test
  fun `rename persistent Id reference as root field`() {
    val newName = "ant"
    val builder = TypedEntityStorageBuilder.create()
    val maven = builder.addFirstEntity("maven")
    val originSecondEntity = builder.addSecondEntity(maven.persistentId(), "gradle")
    builder.checkConsistency()
    builder.modifyEntity(ModifiableFirstEntity::class.java, maven) {
      name = newName
    }
    val newSecondEntity = builder.entities(SecondEntity::class.java).single()
    assertFalse(newSecondEntity === originSecondEntity)
    assertEquals(newSecondEntity.firstId.name, newName)
  }

  @Test
  fun `rename persistent Id reference as field in inner object`() {
    val newName = "ant"
    val builder = TypedEntityStorageBuilder.create()
    val maven = builder.addFirstEntity("maven")
    val originThirdEntity = builder.addThirdEntity(DummyClass(maven.persistentId()), "gradle")
    builder.checkConsistency()
    builder.modifyEntity(ModifiableFirstEntity::class.java, maven) {
      name = newName
    }
    val newThirdEntity = builder.entities(ThirdEntity::class.java).single()
    assertFalse(newThirdEntity === originThirdEntity)
    assertEquals(newThirdEntity.property1.firstId.name, newName)
  }

  @Test
  fun `rename persistent Id reference as list`() {
    val newName = "ant"
    val builder = TypedEntityStorageBuilder.create()
    val maven = builder.addFirstEntity("maven")
    val gradle = builder.addFirstEntity("gradle")
    val originList = mutableListOf(maven.persistentId(), gradle.persistentId())
    builder.addFourthEntity(originList)
    builder.checkConsistency()
    builder.modifyEntity(ModifiableFirstEntity::class.java, maven) {
      name = newName
    }
    val newList = builder.entities(FourthEntity::class.java).single().property1
    assertFalse(originList === newList)
    assertTrue(newList.map { it.name }.contains(newName))
  }

  @Test
  fun `rename persistent Id reference in all entities`() {
    val newName = "gant"
    val builder = TypedEntityStorageBuilder.create()
    val ant = builder.addFirstEntity("ant")
    val maven = builder.addFirstEntity("maven")
    val gradle = builder.addFirstEntity("gradle")
    val originList = mutableListOf(ant.persistentId(), gradle.persistentId())
    val originListTwo = mutableListOf(maven.persistentId(), ant.persistentId())
    builder.addFourthEntity(originList)
    builder.addFifthEntity(DummyClass1(DummyClass(ant.persistentId()), originListTwo))
    builder.checkConsistency()
    builder.modifyEntity(ModifiableFirstEntity::class.java, ant) {
      name = newName
    }
    val newList = builder.entities(FourthEntity::class.java).single().property1
    assertFalse(originList === newList)
    assertTrue(newList.map { it.name }.contains(newName))

    val dummyClass1 = builder.entities(FifthEntity::class.java).single().property1
    assertTrue(dummyClass1.list.map { it.name }.contains(newName))
    assertEquals(dummyClass1.dummyClass.firstId.name, newName)
  }
}