// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.PEntityStorage
import com.intellij.workspace.api.pstorage.PEntityStorageBuilder
import com.intellij.workspace.api.pstorage.PSerializer
import junit.framework.Assert.*
import org.jetbrains.annotations.TestOnly
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

class TestEntityTypesResolver: EntityTypesResolver {
  private val pluginPrefix = "PLUGIN___"

  override fun getPluginId(clazz: Class<*>): String? = pluginPrefix + clazz.name
  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    Assert.assertEquals(pluginPrefix + name, pluginId)
    return javaClass.classLoader.loadClass(name)
  }
}

fun verifyPSerializationRoundTrip(storage: TypedEntityStorage, virtualFileManager: VirtualFileUrlManager): ByteArray {
  storage as PEntityStorage

  fun assertStorageEquals(expected: PEntityStorage, actual: PEntityStorage) {
    assertEquals(
      actual.entitiesByType.all().keys.sortedBy { it.canonicalName },
      expected.entitiesByType.all().keys.sortedBy { it.canonicalName }
    )

    for ((clazz, expectedEntityFamily) in expected.entitiesByType.all()) {
      val actualEntityFamily = actual.entitiesByType.all().getValue(clazz)

      val expectedEntities = expectedEntityFamily.entities
      val actualEntities = actualEntityFamily.entities
      assertEquals(expectedEntities.size, actualEntities.size)

      fun KType.isList(): Boolean = this.toString().startsWith("kotlin.collections.List")
      for (i in expectedEntities.indices) {
        val expectedEntity = expectedEntities[i]
        val actualEntity = actualEntities[i]
        if (expectedEntity == null) {
          assertNull(actualEntity)
          continue
        }

        for (it in expectedEntity::class.memberProperties) {
          val expectedField = it.getter.call(expectedEntity)
          if (expectedField == null) {
            assertNull(it.getter.call(actualEntity))
            continue
          }


          val retType = expectedField::class
          val objectInstance = retType.objectInstance
          if (objectInstance != null) continue

          var enableCheck = true
          (expectedField as? List<Any>)?.forEach {
            val objInstance = it::class.objectInstance
            if (objInstance != null) {
              enableCheck = false
              return@forEach
            }
          }
          if (!enableCheck) continue

          if (expectedField is LibraryId) {
            assertEquals(expectedField.name, (it.getter.call(actualEntity) as LibraryId).name)
            continue
          }

          assertTrue(expectedField == it.getter.call(actualEntity))
        }
      }
    }
  }

  val serializer = PSerializer(virtualFileManager)

  val stream = ByteArrayOutputStream()
  serializer.serializeCache(stream, storage)

  val byteArray = stream.toByteArray()
  val deserialized = (serializer.deserializeCache(ByteArrayInputStream(byteArray)) as PEntityStorageBuilder).toStorage()

  assertStorageEquals(storage, deserialized)

  return byteArray
}

fun verifySerializationRoundTrip(storage: TypedEntityStorage, virtualFileManager: VirtualFileUrlManager): ByteArray {
  storage as ProxyBasedEntityStorage

  fun assertEntityDataEquals(expected: EntityData, actual: EntityData) {
    Assert.assertEquals(expected.id, actual.id)
    Assert.assertEquals(expected.unmodifiableEntityType, actual.unmodifiableEntityType)
    Assert.assertEquals(expected.properties, actual.properties)
    Assert.assertEquals(expected.entitySource, actual.entitySource)
  }

  fun assertEntityDataSetEquals(expected: Set<EntityData>, actual: Set<EntityData>) {
    Assert.assertEquals(expected.size, actual.size)
    val sortedExpected = expected.sortedBy { it.id }
    val sortedActual = actual.sortedBy { it.id }

    for ((expectedData, actualData) in sortedExpected.zip(sortedActual)) {
      assertEntityDataEquals(expectedData, actualData)
    }
  }

  fun assertStorageEquals(expected: ProxyBasedEntityStorage, actual: ProxyBasedEntityStorage) {
    Assert.assertEquals(expected.referrers.keys, actual.referrers.keys)
    for (key in expected.referrers.keys) {
      Assert.assertEquals(expected.referrers.getValue(key).toSet(), actual.referrers.getValue(key).toSet())
    }

    Assert.assertEquals(expected.entityById.keys, actual.entityById.keys)
    for (id in expected.entityById.keys) {
      assertEntityDataEquals(expected.entityById.getValue(id), actual.entityById.getValue(id))
    }

    Assert.assertEquals(expected.entitiesBySource.keys, actual.entitiesBySource.keys)
    for (source in expected.entitiesBySource.keys) {
      assertEntityDataSetEquals(expected.entitiesBySource.getValue(source), actual.entitiesBySource.getValue(source))
    }

    Assert.assertEquals(expected.entitiesByType.keys, actual.entitiesByType.keys)
    for (type in expected.entitiesByType.keys) {
      assertEntityDataSetEquals(expected.entitiesByType.getValue(type), actual.entitiesByType.getValue(type))
    }

    Assert.assertEquals(expected.entitiesByPersistentIdHash.keys, actual.entitiesByPersistentIdHash.keys)
    for (idHash in expected.entitiesByPersistentIdHash.keys) {
      assertEntityDataSetEquals(expected.entitiesByPersistentIdHash.getValue(idHash), actual.entitiesByPersistentIdHash.getValue(idHash))
    }
  }

  val serializer = KryoEntityStorageSerializer(TestEntityTypesResolver(), virtualFileManager)

  val stream = ByteArrayOutputStream()
  serializer.serializeCache(stream, storage)

  val byteArray = stream.toByteArray()
  val deserialized = serializer.deserializeCache(ByteArrayInputStream(byteArray)) as ProxyBasedEntityStorage

  assertStorageEquals(storage, deserialized)

  return byteArray
}

@TestOnly
fun TypedEntityStorage.toGraphViz(): String {
  this as ProxyBasedEntityStorage

  val sb = StringBuilder()
  sb.appendln("digraph G {")

  val visited = mutableSetOf<Long>()

  for ((id, refs) in referrers) {
    visited.add(id)
    for (ref in refs) {
      visited.add(ref)
      sb.appendln("""  "${entityById.getValue(ref)}" -> "${entityById.getValue(id)}";""")
    }
  }

  for ((id, data) in entityById) {
    if (!visited.contains(id)) {
      sb.appendln("""  "$data";""")
    }
  }

  sb.appendln("}")

  return sb.toString()
}
