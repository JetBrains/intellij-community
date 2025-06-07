// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.metadata.serialization

import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class ClassesMetadataSerializationTest: MetadataSerializationTest() {
  // SEALED CLASSES
  @Test
  fun `simple sealed class entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClassEntity(
      text = "cache version",
      someData = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass.FirstKeyPropDataClass("cache version"),
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClassEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClassEntity"
        Start comparing cache: Own property "someData"     with current: Own property "someData"
          Start comparing cache: Custom type     with current: Custom type
            Start comparing cache: Abstract class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass"     with current: Abstract class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass"
              Start comparing cache: Final class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass${'$'}SecondKeyPropDataClass"     with current: Final class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass${'$'}SecondKeyPropDataClass"
                Cache: supertypes = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass, Current: supertypes = com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass    Result: not equal
              End comparing cache: Final class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass${'$'}SecondKeyPropDataClass"     with current: Final class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass${'$'}SecondKeyPropDataClass"    Result: not equal
            End comparing cache: Abstract class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClass"     with current: Abstract class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass"    Result: not equal
          End comparing cache: Custom type     with current: Custom type    Result: not equal
        End comparing cache: Own property "someData"     with current: Own property "someData"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SimpleSealedClassEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClassEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
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
  @Test
  fun `changed enum name entity`() {
    val builder = createEmptyBuilder()
    
    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEntity(
      someEnum = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum.A_ENTRY,
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
       Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEntity"
         Start comparing cache: Own property "someEnum"     with current: Own property "someEnum"
           Start comparing cache: Custom type     with current: Custom type
             Start comparing cache: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum"     with current: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEnum"
               Cache: enum entries = CA_ENTRY, Current: enum entries = CB_ENTRY    Result: not equal
             End comparing cache: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEnum"     with current: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEnum"    Result: not equal
           End comparing cache: Custom type     with current: Custom type    Result: not equal
         End comparing cache: Own property "someEnum"     with current: Own property "someEnum"    Result: not equal
       End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedEnumNameEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedEnumNameEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
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
  @Test
  fun `subset enum entity`() {
    val builder = createEmptyBuilder()

    builder addEntity com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity(
      someEnum = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum.C_ENUM,
      SampleEntitySource("test")
    )

    val cacheDiff = calculateCacheDiff(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    Assert.assertEquals("""
      Start comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEntity"
        Start comparing cache: Own property "someEnum"     with current: Own property "someEnum"
          Start comparing cache: Custom type     with current: Custom type
            Start comparing cache: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum"     with current: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEnum"
              Cache: enum entries = C_ENUM, Current: enum entries = B_ENUM    Result: not equal
            End comparing cache: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEnum"     with current: Enum class "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEnum"    Result: not equal
          End comparing cache: Custom type     with current: Custom type    Result: not equal
        End comparing cache: Own property "someEnum"     with current: Own property "someEnum"    Result: not equal
      End comparing cache: Entity "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.SubsetEnumEntity"     with current: Entity "com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetEnumEntity"    Result: not equal

    """.trimIndent(), cacheDiff)
  }
}