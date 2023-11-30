// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.serialization.UnsupportedEntitiesVersionException
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.junit.Ignore
import org.junit.Test

class ClassesMetadataSerializationTest: MetadataSerializationTest() {
  // SEALED CLASSES
  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `simple sealed class entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClassEntity(
      text = "cache version",
      someData = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass.FirstKeyPropDataClass("cache version"),
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Ignore("Disabled while the hash is naively counted")
  @Test //cache version and current version should be the same
  fun `subset sealed class entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClassEntity(
      someData = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetSealedClass.FirstSubsetSealedClassObject,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }



  // ENUMS
  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `changed enum name entity`() {
    val builder = createEmptyBuilder()
    
    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEntity(
      someEnum = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum.FIRST,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test //cache version and current version should be the same
  fun `enum props entity`() {
    val builder = createEmptyBuilder()
    
    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEntity(
      someEnum = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEnum.SECOND,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  //TODO("Fix it")
  @Test(expected = UnsupportedEntitiesVersionException::class) //cache version and current version should be different
  fun `subset enum entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity(
      someEnum = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum.FIFTH,
      SampleEntitySource("test")
    )

    MetadataSerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }
}