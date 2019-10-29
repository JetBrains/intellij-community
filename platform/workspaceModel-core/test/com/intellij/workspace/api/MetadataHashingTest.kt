package com.intellij.workspace.api

import org.junit.Assert
import org.junit.Test

class MetadataHashingTest {
  @Test
  fun noChanges() {
    Assert.assertEquals(entityHash(NoChangesType1::class.java), entityHash(NoChangesType2::class.java))
  }

  @Test
  fun enumChangesOrder() {
    Assert.assertNotEquals(entityHash(EnumEntityType1::class.java), entityHash(EnumEntityType2::class.java))
  }

  @Test
  fun entityValueTypeChanges() {
    Assert.assertNotEquals(entityHash(EntityValueType1::class.java), entityHash(EntityValueType2::class.java))
  }

  @Test
  fun addField() {
    Assert.assertNotEquals(entityHash(AddFieldType1::class.java), entityHash(AddFieldType2::class.java))
  }

  @Test
  fun changeListEntity() {
    Assert.assertNotEquals(entityHash(ChangeListEntityType1::class.java), entityHash(ChangeListEntityType2::class.java))
  }

  @Test
  fun idFieldChange() {
    Assert.assertNotEquals(entityHash(IdFieldChangeType1::class.java), entityHash(IdFieldChangeType2::class.java))
  }

  @Test
  fun changeToList() {
    Assert.assertNotEquals(entityHash(ChangeToListType1::class.java), entityHash(ChangeToListType2::class.java))
  }

  private val registry = EntityMetaDataRegistry()

  private fun entityHash(clazz: Class<out TypedEntity>): String =
    registry.getEntityMetaData(clazz)
      .hash(registry, hasher = { DebuggingHasher() })
      .toString(Charsets.UTF_8)
      .replace("Type1", "Type")
      .replace("Type2", "Type")

  interface NoChangesType1: TypedEntity {
    val e: EnumType
    val m: List<Ent>
    val t: Ent

    enum class EnumType {
      A, B, C
    }

    interface Ent: TypedEntity {
      val x: Int
      val y: String
    }
  }

  interface NoChangesType2: TypedEntity {
    val t: Ent
    val e: EnumType
    val m: List<Ent>

    enum class EnumType {
      A, B, C
    }

    interface Ent: TypedEntity {
      val x: Int
      val y: String
    }
  }

  interface EnumEntityType1: TypedEntity {
    val e: EnumType1

    enum class EnumType1 {
      A, B, C
    }
  }

  interface EnumEntityType2: TypedEntity {
    val e: EnumType2

    enum class EnumType2 {
      A, C, B
    }
  }

  interface EntityValueType1: TypedEntity {
    val x: X

    interface X: TypedEntity {
      val s: String
    }
  }

  interface EntityValueType2: TypedEntity {
    val x: Y

    interface Y: TypedEntity {
      val s: String
    }
  }

  interface AddFieldType1: TypedEntity {
    val f1: String
  }

  interface AddFieldType2: TypedEntity {
    val f1: String
    val f2: String
  }

  interface ChangeListEntityType1: TypedEntity {
    val f1: List<E>
    interface E: TypedEntity {
      val s: String
    }
  }

  interface ChangeListEntityType2: TypedEntity {
    val f1: List<E>
    interface E: TypedEntity {
      val s: Int
    }
  }

  interface IdFieldChangeType1: TypedEntity {
    val xyz: MyId
    data class MyId(val z: Int): PersistentEntityId<E>(E::class) {
      override val parentId: PersistentEntityId<*>?
        get() = null
      override val presentableName: String
        get() = "XXX"
    }
    interface E: TypedEntityWithPersistentId {
      val s: Int
    }
  }

  interface IdFieldChangeType2: TypedEntity {
    val xyz: MyId
    data class MyId(val z: String): PersistentEntityId<E>(E::class) {
      override val parentId: PersistentEntityId<*>?
        get() = null
      override val presentableName: String
        get() = "XXX"
    }
    interface E: TypedEntityWithPersistentId {
      val s: Int
    }
  }

  interface ChangeToListType1: TypedEntity {
    val x: String
  }

  interface ChangeToListType2: TypedEntity {
    val x: List<String>
  }
}
