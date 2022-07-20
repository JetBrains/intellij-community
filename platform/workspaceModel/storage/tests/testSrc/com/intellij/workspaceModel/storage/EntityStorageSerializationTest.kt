// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryTableId
import com.intellij.workspaceModel.storage.entities.test.addSampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntity
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntitySource
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import junit.framework.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

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
  fun `entity list and map builder serialization`() {
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

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toSnapshot())

    val byteArray = stream.toByteArray()
    val deserialized = (deserializer.deserializeCache(ByteArrayInputStream(byteArray)) as? MutableEntityStorageImpl)?.toSnapshot()

    assertNull(deserialized)
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

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toSnapshot())
  }

  @Test
  fun `serialize abstract`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addSampleEntity("myString")

    val stream = ByteArrayOutputStream()
    val result = serializer.serializeCache(stream, builder.toSnapshot())

    assertTrue(result is SerializationResult.Success)
  }

  @Test
  fun `read broken cache`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addSampleEntity("myString")

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toSnapshot())

    // Remove random byte from a serialised store
    val inputStream = stream.toByteArray().filterIndexed { i, _ -> i != 3 }.toByteArray().inputStream()

    val result = serializer.deserializeCache(inputStream)

    assertNull(result)
  }
}

// Kotlin tip: Use the ugly ${'$'} to insert the $ into the multiline string
private val expectedKryoRegistration = """
  [10, com.intellij.workspaceModel.storage.impl.ConnectionId]
  [11, com.intellij.workspaceModel.storage.impl.ImmutableEntitiesBarrel]
  [12, com.intellij.workspaceModel.storage.impl.ChildEntityId]
  [13, com.intellij.workspaceModel.storage.impl.ParentEntityId]
  [14, it.unimi.dsi.fastutil.objects.ObjectOpenHashSet]
  [15, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}TypeInfo]
  [16, java.util.ArrayList]
  [17, java.util.HashMap]
  [18, com.intellij.util.SmartList]
  [19, java.util.LinkedHashMap]
  [20, com.intellij.workspaceModel.storage.impl.containers.BidirectionalMap]
  [21, com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap]
  [22, java.util.HashSet]
  [23, com.intellij.util.containers.BidirectionalMultiMap]
  [24, com.google.common.collect.HashBiMap]
  [25, java.util.LinkedHashSet]
  [26, com.intellij.workspaceModel.storage.impl.containers.LinkedBidirectionalMap]
  [27, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [28, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap]
  [29, java.util.List]
  [30, kotlin.collections.builders.ListBuilder]
  [31, kotlin.collections.builders.MapBuilder]
  [32, java.util.Arrays${'$'}ArrayList]
  [33, byte[]]
  [34, com.intellij.workspaceModel.storage.impl.ImmutableEntityFamily]
  [35, com.intellij.workspaceModel.storage.impl.RefsTable]
  [36, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntBiMap]
  [37, com.intellij.workspaceModel.storage.impl.containers.ImmutableIntIntUniqueBiMap]
  [38, com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex]
  [39, com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex]
  [40, com.intellij.workspaceModel.storage.impl.indices.PersistentIdInternalIndex]
  [41, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntMultiMap${'$'}ByList]
  [42, int[]]
  [43, kotlin.Pair]
  [44, com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex]
  [45, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}SerializableEntityId]
  [46, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}AddEntity]
  [47, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [48, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [49, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [50, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [51, java.util.Collections${'$'}UnmodifiableCollection]
  [52, java.util.Collections${'$'}UnmodifiableSet]
  [53, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [54, java.util.Collections${'$'}UnmodifiableMap]
  [55, java.util.Collections${'$'}EmptyList]
  [56, java.util.Collections${'$'}EmptyMap]
  [57, java.util.Collections${'$'}EmptySet]
  [58, java.util.Collections${'$'}SingletonList]
  [59, java.util.Collections${'$'}SingletonMap]
  [60, java.util.Collections${'$'}SingletonSet]
  [61, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [62, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [63, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [64, kotlin.collections.EmptyMap]
  [65, kotlin.collections.EmptyList]
  [66, kotlin.collections.EmptySet]
  [67, Object[]]
  [68, java.util.UUID]
""".trimIndent()
