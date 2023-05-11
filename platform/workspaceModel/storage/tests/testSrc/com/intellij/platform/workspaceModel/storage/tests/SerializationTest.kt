// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.platform.workspaceModel.storage.testEntities.entities.*
import com.intellij.workspaceModel.storage.EntityInformation
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {
  @Test
  @Ignore
  fun `test bool data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(BooleanEntity(true, MySource) {
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(BooleanEntity(false, MySource) {
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertTrue(target.entities(BooleanEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test string data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(StringEntity("One", MySource) {
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(StringEntity("", MySource) {
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals("One", target.entities(StringEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test Int data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(IntEntity(1, MySource) {
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(IntEntity(0, MySource) {
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals(1, target.entities(IntEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test list data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(ListEntity(listOf("data"), MySource) {
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(ListEntity(listOf(), MySource) {
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals(listOf("data"), target.entities(ListEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test optional int data with null data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(OptionalIntEntity(MySource) {
      data = null
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(OptionalIntEntity(MySource) {
      data = 1
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals(null, target.entities(OptionalIntEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test optional int data with not null data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(OptionalIntEntity(MySource) {
      data = 1
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(OptionalIntEntity(MySource) {
      data = null
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals(1, target.entities(OptionalIntEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test optional string data with null data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(OptionalStringEntity(MySource) {
      data = null
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(OptionalStringEntity(MySource) {
      data = "1"
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals(null, target.entities(OptionalStringEntity::class.java).single().data)
  }

  @Test
  @Ignore
  fun `test optional string data with not null data`() {
    val builder = createEmptyBuilder()
    builder.addEntity(OptionalStringEntity(MySource) {
      data = "1"
    })

    val entityData = builder.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()

    val serializer = TestSerializer()
    entityData.serialize(serializer)

    val target = createEmptyBuilder()
    target.addEntity(OptionalStringEntity(MySource) {
      data = null
    })
    val targetEntityData = target.entitiesByType.entityFamilies.filterNotNull().first().entities.filterNotNull().first()
    targetEntityData.deserialize(serializer)

    assertEquals("1", target.entities(OptionalStringEntity::class.java).single().data)
  }
}

class TestSerializer : EntityInformation.Serializer, EntityInformation.Deserializer {

  private val builder = StringBuilder()

  override fun saveInt(i: Int) {
    builder.appendLine(i)
  }

  override fun saveString(s: String) {
    builder.appendLine(s)
  }

  override fun saveBoolean(b: Boolean) {
    builder.appendLine(b)
  }

  override fun saveBlob(b: Any, javaSimpleName: String) {
    TODO("Not yet implemented")
  }

  override fun saveNull() {
    builder.appendLine("null")
  }

  override fun readBoolean(): Boolean {
    return getData().toBooleanStrict()
  }

  override fun readString(): String {
    return getData()
  }

  override fun readInt(): Int {
    return getData().toInt()
  }

  override fun acceptNull(): Boolean {
    val newLineIndex = builder.indexOf('\n')
    val str = builder.substring(0, newLineIndex)
    if (str == "null") {
      builder.delete(0, newLineIndex + 1)
      return true
    }
    return false
  }

  private fun getData(): String {
    val newLineIndex = builder.indexOf('\n')
    val str = builder.substring(0, newLineIndex)
    builder.delete(0, newLineIndex + 1)
    return str
  }
}
