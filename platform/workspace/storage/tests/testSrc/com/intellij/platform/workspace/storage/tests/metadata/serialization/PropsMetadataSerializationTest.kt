// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.serialization.UnsupportedEntitiesVersionException
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.junit.Ignore
import org.junit.Test

class PropsMetadataSerializationTest: MetadataSerializationTest() {
  // COMPUTABLE PROPERTIES
  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `computable prop entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ComputablePropEntity(
      list = listOf(),
      value = 5,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `changed computable prop entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropEntity(
      text = "cache version",
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
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

  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `null to not null entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.NullToNotNullEntity(
      notNullBoolean = false,
      notNullInt = 5,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
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