// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.ClassToIntConverter
import com.intellij.platform.workspace.storage.impl.ImmutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.metadata.diff.fqnsComparator
import com.intellij.platform.workspace.storage.metadata.diff.replaceMetadataTypesFqnComparator
import com.intellij.platform.workspace.storage.metadata.resolver.TypeMetadataResolver
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestClassToIntConverter
import com.intellij.platform.workspace.storage.tests.metadata.serialization.service.TestTypeMetadataResolver
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.After
import org.junit.Before
import java.nio.file.Files

abstract class MetadataSerializationTest {
  private val classToIntConverter = ClassToIntConverter.getInstance()
  private val typeMetadataResolver = TypeMetadataResolver.getInstance()
  private val metadataTypesFqnComparator = fqnsComparator

  @Before
  fun setUp() {
    ClassToIntConverter.replaceClassToIntConverter(TestClassToIntConverter(classToIntConverter))
    TypeMetadataResolver.replaceTypeMetadataResolver(TestTypeMetadataResolver(typeMetadataResolver))
    replaceMetadataTypesFqnComparator(getTestMetadataTypesFqnComparator())
  }

  @After
  fun tearDown() {
    ClassToIntConverter.replaceClassToIntConverter(classToIntConverter)
    TypeMetadataResolver.replaceTypeMetadataResolver(typeMetadataResolver)
    replaceMetadataTypesFqnComparator(metadataTypesFqnComparator)
  }

  fun calculateCacheDiff(storage: EntityStorage, virtualFileManager: VirtualFileUrlManager): String {
    deserialization = false
    storage as ImmutableEntityStorageImpl
    storage.assertConsistency()

    val serializer = EntityStorageSerializerImpl(MetadataDiffTestResolver, virtualFileManager, ijBuildVersion = "")

    val file = Files.createTempFile("", "")
    try {
      serializer.serializeCache(file, storage)

      deserialization = true
      return serializer.calculateCacheDiff(file)
    }
    finally {
      Files.deleteIfExists(file)
    }
  }

  private fun getTestMetadataTypesFqnComparator(): (String, String) -> Boolean =
    { cache, current -> cache.replaceCacheVersion() == current }
}