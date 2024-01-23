// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.serialization.UnsupportedEntitiesVersionException
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.junit.Ignore
import org.junit.Test

class SimpleMetadataSerializationTest: MetadataSerializationTest() {

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `changed props order entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderEntity(
      version = 1,
      string = "cache version",
      list = listOf(hashSetOf(1, 2, 3), hashSetOf()),
      data = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderDataClass(5, "cache data"),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `changed value type entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity(
      type = "cache version",
      someKey = 2,
      text = listOf("some text", "some string", "more text"),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `simple props entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity(
      text = "cache version",
      list = listOf(1, 2, 3),
      set = setOf(listOf("1", "2"), listOf("3")),
      map = mapOf(),
      bool = false,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `one to many ref entity`() {
    val builder = createEmptyBuilder()

    val oneToManyRefEntity = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity(
      someData = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass(listOf(), 5),
      SampleEntitySource("test")
    )

    builder addEntity oneToManyRefEntity

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity(
      version = 1,
      someData = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass(listOf(), 5),
      SampleEntitySource("test")
    ) {
      parentEntity = oneToManyRefEntity
    }

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToManyRefEntity(
      version = 2,
      someData = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefDataClass(listOf(hashSetOf("something"), hashSetOf("text")), 4),
      SampleEntitySource("test")
    ) {
      parentEntity = oneToManyRefEntity
    }

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `one to one ref entity`() {
    val builder = createEmptyBuilder()

    val oneToOneRefEntity = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity(
      version = 1,
      text = "cache version",
      SampleEntitySource("test")
    )

    builder addEntity oneToOneRefEntity

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity(
      someString = "cache version",
      boolean = false,
      SampleEntitySource("test")
    ) {
      parentEntity = oneToOneRefEntity
    }

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `simple objects reference entity`() {
    val builder = createEmptyBuilder()

    val oneToOneRefEntity = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity(
      version = 1,
      text = "cache version",
      SampleEntitySource("test")
    )

    builder addEntity oneToOneRefEntity

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity(
      someString = "cache version",
      boolean = false,
      SampleEntitySource("test")
    ) {
      parentEntity = oneToOneRefEntity
    }

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Ignore("Disabled while the hash is naively counted")
  @Test //cache version and current version should be the same
  fun `key prop entity`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.KeyPropEntity(
      someInt = 5,
      text = "cache version",
      url = virtualFileManager.getOrCreateFromUri("file:///tmp"),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), virtualFileManager)
  }
}