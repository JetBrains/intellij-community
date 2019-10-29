package com.intellij.workspace.api

import org.jetbrains.annotations.TestOnly
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TestEntityTypesResolver: EntityTypesResolver {
  private val pluginPrefix = "PLUGIN___"

  override fun getPluginId(clazz: Class<*>): String? = pluginPrefix + clazz.name
  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    Assert.assertEquals(pluginPrefix + name, pluginId)
    return javaClass.classLoader.loadClass(name)
  }
}

fun verifySerializationRoundTrip(storage: TypedEntityStorage, serializer: EntityStorageSerializer): ByteArray {
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
