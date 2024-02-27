// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class SimpleMetadataSerializationTest: MetadataSerializationTest() {

  @Test
  fun `changed props order entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderEntity(
      version = 1,
      string = "cache version",
      list = listOf(hashSetOf(1, 2, 3), hashSetOf()),
      data = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderDataClass(5, "cache data"),
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderEntity"
        Start comparing cache: Own property "list"     with current: Own property "data"
          Cache: name = list, Current: name = data    Result: not equal
        End comparing cache: Own property "list"     with current: Own property "data"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedPropsOrderEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedPropsOrderEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Test
  fun `changed value type entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity(
      type = "cache version",
      someKey = 2,
      text = listOf("some text", "some string", "more text"),
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedValueTypeEntity"
        Start comparing cache: Own property "someKey"     with current: Own property "someKey"
          Start comparing cache: Primitive type     with current: Primitive type
            Cache: primitive type = Int, Current: primitive type = String    Result: not equal
          End comparing cache: Primitive type     with current: Primitive type    Result: not equal
        End comparing cache: Own property "someKey"     with current: Own property "someKey"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedValueTypeEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedValueTypeEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Test
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

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity"
        Start comparing cache: Own property "anotherEntity"     with current: Own property "anotherEntity"
          Start comparing cache: Entity reference     with current: Entity reference
            Cache: connectionType = ONE_TO_MANY, Current: connectionType = ONE_TO_ONE    Result: not equal
          End comparing cache: Entity reference     with current: Entity reference    Result: not equal
        End comparing cache: Own property "anotherEntity"     with current: Own property "anotherEntity"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToManyRefEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToManyRefEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Test
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

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimplePropsEntity"
        Start comparing cache: Own property "map"     with current: Own property "map"
          Start comparing cache: Parametrized type     with current: Parametrized type
            Start comparing cache: Parametrized type     with current: Parametrized type
              Start comparing cache: Primitive type     with current: Primitive type
                Cache: primitive type = Int, Current: primitive type = String    Result: not equal
              End comparing cache: Primitive type     with current: Primitive type    Result: not equal
            End comparing cache: Parametrized type     with current: Parametrized type    Result: not equal
          End comparing cache: Parametrized type     with current: Parametrized type    Result: not equal
        End comparing cache: Own property "map"     with current: Own property "map"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimplePropsEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimplePropsEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Test
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

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity"
        Start comparing cache: Own property "anotherEntity"     with current: Own property "anotherEntity"
          Start comparing cache: Entity reference     with current: Entity reference
            Cache: connectionType = ONE_TO_ONE, Current: connectionType = ONE_TO_MANY    Result: not equal
          End comparing cache: Entity reference     with current: Entity reference    Result: not equal
        End comparing cache: Own property "anotherEntity"     with current: Own property "anotherEntity"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Test
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

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity"
        Start comparing cache: Own property "anotherEntity"     with current: Own property "anotherEntity"
          Start comparing cache: Entity reference     with current: Entity reference
            Cache: connectionType = ONE_TO_ONE, Current: connectionType = ONE_TO_MANY    Result: not equal
          End comparing cache: Entity reference     with current: Entity reference    Result: not equal
        End comparing cache: Own property "anotherEntity"     with current: Own property "anotherEntity"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.OneToOneRefEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Ignore("Disabled while the hash is naively counted")
  @Test //cache version and current version should be the same
  fun `key prop entity`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.KeyPropEntity(
      someInt = 5,
      text = "cache version",
      url = virtualFileManager.getOrCreateFromUrl("file:///tmp"),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), virtualFileManager)
  }
}