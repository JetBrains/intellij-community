// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class PropsMetadataSerializationTest: MetadataSerializationTest() {
  // COMPUTABLE PROPERTIES
  @Test
  fun `computable prop entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity(
      list = listOf(),
      value = 5,
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ComputablePropEntity"
        Sizes of cache properties (3) and current properties (4) are different    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ComputablePropEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Test
  fun `changed computable prop entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntity(
      text = "cache version",
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Final class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId"     with current: Final class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId"
        Start comparing cache: Own property "text"     with current: Own property "texts"
          Cache: name = text, Current: name = texts    Result: not equal
        End comparing cache: Own property "text"     with current: Own property "texts"    Result: not equal
      End comparing cache: Final class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntityId"     with current: Final class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId"    Result: not equal

    """.trimIndent(), cacheDiff)
  }

  @Ignore("Disabled while the hash is naively counted")
  @Test //cache version and current version should be the same
  fun `changed computable props order`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntity(
      someKey = 5,
      names = listOf("Module", "Artifact", "Library"),
      value = 10,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }



  // NULLABLE PROPERTIES

  @Ignore("Disabled while the hash is naively counted")
  @Test //cache version and current version should be the same
  fun `not null to null entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NotNullToNullEntity(
      notNullString = "string",
      notNullList = listOf(5, 4, 3),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `null to not null entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NullToNotNullEntity(
      notNullBoolean = false,
      notNullInt = 5,
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NullToNotNullEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NullToNotNullEntity"
        Start comparing cache: Own property "nullString"     with current: Own property "nullString"
          Start comparing cache: Primitive type     with current: Primitive type
            Cache: isNullable = true, Current: isNullable = false    Result: not equal
          End comparing cache: Primitive type     with current: Primitive type    Result: not equal
        End comparing cache: Own property "nullString"     with current: Own property "nullString"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NullToNotNullEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.NullToNotNullEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }


  // DEFAULT PROPERTIES
  @Ignore("Disabled while the hash is naively counted")
  @Test //cache version and current version should be the same
  fun `default prop entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.DefaultPropEntity(
      someString = "cache version",
      someList = listOf(1, 2, 3),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }
}