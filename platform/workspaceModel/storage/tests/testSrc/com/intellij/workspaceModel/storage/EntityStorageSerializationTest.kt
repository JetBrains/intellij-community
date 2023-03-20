// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.entities.test.addSampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class EntityStorageSerializationTest {
  @Test
  fun `simple model serialization`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity("MyEntity")

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `entity properties serialization`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity(stringProperty = "MyEntity",
                            stringListProperty = mutableListOf("a", "b"),
                            stringSetProperty = mutableSetOf("c", "d"))

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `entity collections and map serialization`() {
    val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl()
    val builder = createEmptyBuilder()
    val stringListProperty = buildList {
      this.add("a")
      this.add("b")
    }
    val stringMapProperty = buildMap {
      put("ab", "bc")
      put("bc", "ce")
    }
    val entity = SampleEntity(false, "MyEntity", stringListProperty,
                              stringMapProperty, virtualFileManager.fromUrl("file:///tmp"), SampleEntitySource("test"))
    builder.addEntity(entity)

    val setProperty = buildSet {
      this.add(1)
      this.add(2)
      this.add(2)
    }
    builder.addEntity(CollectionFieldEntity(setProperty, listOf("one", "two", "three"), MySource))
    builder.addEntity(CollectionFieldEntity(setOf(1,2,3,3,4), listOf("one", "two", "three"), MySource))

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), virtualFileManager)
  }

  @Test
  fun `entity uuid serialization`() {
    val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl()
    val builder = createEmptyBuilder()
    val entity = SampleEntity(false, "MyEntity", emptyList(),
                              emptyMap(), virtualFileManager.fromUrl("file:///tmp"), SampleEntitySource("test")) {
      randomUUID = UUID.fromString("58e0a7d7-eebc-11d8-9669-0800200c9a66")
    }
    builder.addEntity(entity)

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), virtualFileManager)
  }

  @Test
  fun `serialization with version changing`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity("MyEntity")

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
    val deserializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
      .also { it.serializerDataFormatVersion = "XYZ" }

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())

      val deserialized = (deserializer.deserializeCache(file).getOrThrow() as? MutableEntityStorageImpl)?.toSnapshot()
      assertNull(deserialized)
    }
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())

    val (kryo, _) = serializer.createKryo()

    val registration = (10..1_000).mapNotNull { kryo.getRegistration(it) }.joinToString(separator = "\n")

    assertEquals("Have you changed kryo registration? Update the version number! (And this test)", expectedKryoRegistration, registration)
  }

  @Test
  fun `serialize empty lists`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    // Do not replace ArrayList() with emptyList(). This must be a new object for this test
    builder.addLibraryEntity("myName", LibraryTableId.ProjectLibraryTableId, ArrayList(), ArrayList(), MySource)

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())
    }
  }

  @Test
  fun `serialize abstract`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addSampleEntity("myString")

    withTempFile { file ->
      val result = serializer.serializeCache(file, builder.toSnapshot())
      assertTrue(result is SerializationResult.Success)
    }
  }

  @Test
  fun `serialize rider like`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addEntity(ProjectModelTestEntity("info", DescriptorInstance("info"), MySource))

    withTempFile { file ->
      val result = serializer.serializeCache(file, builder.toSnapshot())
      assertTrue(result is SerializationResult.Success)
    }
  }

  @Test
  fun `read broken cache`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addSampleEntity("myString")

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())

      // Remove random byte from a serialised store
      Files.write(file, Files.readAllBytes(file).filterIndexed { i, _ -> i != 3 }.toByteArray())
      val result = serializer.deserializeCache(file)
      assertNull(result.getOrNull())
    }
  }
}

// Kotlin tip: Use the ugly ${'$'} to insert the $ into the multiline string
private val expectedKryoRegistration = """
  [10, com.intellij.workspaceModel.storage.impl.ConnectionId]
  [11, com.intellij.workspaceModel.storage.impl.ImmutableEntitiesBarrel]
  [12, com.intellij.workspaceModel.storage.impl.ChildEntityId]
  [13, com.intellij.workspaceModel.storage.impl.ParentEntityId]
  [14, it.unimi.dsi.fastutil.objects.ObjectOpenHashSet]
  [15, com.intellij.workspaceModel.storage.impl.indices.SymbolicIdInternalIndex]
  [16, com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex]
  [17, com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex]
  [18, com.intellij.workspaceModel.storage.impl.containers.BidirectionalLongMultiMap]
  [19, it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap]
  [20, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap]
  [21, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}TypeInfo]
  [22, java.util.List]
  [23, java.util.ArrayList]
  [24, java.util.HashMap]
  [25, com.intellij.util.SmartList]
  [26, java.util.LinkedHashMap]
  [27, com.intellij.workspaceModel.storage.impl.containers.BidirectionalMap]
  [28, com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap]
  [29, com.intellij.util.containers.BidirectionalMultiMap]
  [30, com.google.common.collect.HashBiMap]
  [31, java.util.LinkedHashSet]
  [32, com.intellij.workspaceModel.storage.impl.containers.LinkedBidirectionalMap]
  [33, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [34, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap]
  [35, byte[]]
  [36, com.intellij.workspaceModel.storage.impl.ImmutableEntityFamily]
  [37, com.intellij.workspaceModel.storage.impl.RefsTable]
  [38, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntBiMap]
  [39, com.intellij.workspaceModel.storage.impl.containers.ImmutableIntIntUniqueBiMap]
  [40, com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex]
  [41, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntMultiMap${'$'}ByList]
  [42, int[]]
  [43, kotlin.Pair]
  [44, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}SerializableEntityId]
  [45, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}AddEntity]
  [46, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [47, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [48, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [49, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [50, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}Data]
  [51, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}References]
  [52, java.util.Collections${'$'}UnmodifiableCollection]
  [53, java.util.Collections${'$'}UnmodifiableSet]
  [54, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [55, java.util.Collections${'$'}UnmodifiableMap]
  [56, java.util.Collections${'$'}EmptyList]
  [57, java.util.Collections${'$'}EmptyMap]
  [58, java.util.Collections${'$'}EmptySet]
  [59, java.util.Collections${'$'}SingletonList]
  [60, java.util.Collections${'$'}SingletonMap]
  [61, java.util.Collections${'$'}SingletonSet]
  [62, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [63, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [64, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [65, kotlin.collections.EmptyMap]
  [66, kotlin.collections.EmptyList]
  [67, kotlin.collections.EmptySet]
  [68, Object[]]
  [69, java.util.UUID]
""".trimIndent()

private inline fun withTempFile(task: (file: Path) -> Unit) {
  val file = Files.createTempFile("", "")
  try {
    task(file)
  }
  finally {
    Files.deleteIfExists(file)
  }
}
