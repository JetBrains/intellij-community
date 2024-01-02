// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.tests.BaseSerializationChecker
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.ReflectionUtil
import org.junit.Assert
import java.nio.file.Files

internal var deserialization = false

object MetadataDiffTestResolver: EntityTypesResolver {
  private val pluginPrefix = "PLUGIN___"

  override fun getPluginId(clazz: Class<*>): String = pluginPrefix + clazz.name

  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    return resolveClass(
      if (deserialization) name.replaceCacheVersion() else name
    )
  }

  override fun getClassLoader(pluginId: String?): ClassLoader? {
    return javaClass.classLoader
  }

  fun resolveClass(name: String): Class<*> {
    if (name.startsWith("[")) return Class.forName(name)
    return javaClass.classLoader.loadClass(name)
  }
}

object MetadataSerializationRoundTripChecker: BaseSerializationChecker() {

  override fun verifyPSerializationRoundTrip(storage: EntityStorage, virtualFileManager: VirtualFileUrlManager): ByteArray {
    deserialization = false
    storage as ImmutableEntityStorageImpl
    storage.assertConsistency()

    val serializer = EntityStorageSerializerImpl(MetadataDiffTestResolver, virtualFileManager)

    val file = Files.createTempFile("", "")
    try {
      serializer.serializeCache(file, storage)

      deserialization = true
      val deserialized = (serializer.deserializeCache(file).getOrThrow() as MutableEntityStorageImpl)
        .toSnapshot() as ImmutableEntityStorageImpl
      deserialized.assertConsistency()

      assertStorageEquals(storage, deserialized)
      deserialization = false
      storage.assertConsistency()
      return Files.readAllBytes(file)
    }
    finally {
      Files.deleteIfExists(file)
    }
  }

  override fun <T> valuesComparator(expected: T, actual: T): Boolean = expected.toString() == actual.toString()

  override fun assertEntitiesEqual(expected: List<WorkspaceEntityData<*>?>, actual: List<WorkspaceEntityData<*>?>) =
    Assert.assertTrue(equals(expected, actual) { a, b -> a == null && b == null || a != null && b != null && fieldsToString(a) == fieldsToString(b) })

  private fun fieldsToString(any: Any?): String {
    if (any == null) {
      return "null"
    }

    if (any is Enum<*>) {
      return any.toString()
    }

    val fields = ReflectionUtil.collectFields(any.javaClass).toList()
      .filterNot { it.name == "Companion" }
      .filterNot { it.name == "INSTANCE" }
      .onEach { it.isAccessible = true }
      .joinToString(separator = ", ") { f ->
        "${f.name}=${
          if (f.type.name.contains(NEW_VERSION_PACKAGE_NAME) || f.type.name.contains(CACHE_VERSION_PACKAGE_NAME))
            fieldsToString(f.get(any))
          else
            f.get(any)
        }"
      }

    val result = "${any::class.simpleName}($fields)"
    return result
  }


}