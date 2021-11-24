// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import junit.framework.Assert.*
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EntityStorageSerializationTest {
  @Test
  fun `simple model serialization`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity("MyEntity")

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toStorage(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `entity properties serialization`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity(stringProperty = "MyEntity",
                            stringListProperty = mutableListOf("a", "b"),
                            stringSetProperty = mutableSetOf("c", "d"))

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toStorage(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `serialization with version changing`() {
    val builder = createEmptyBuilder()
    builder.addSampleEntity("MyEntity")

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
    val deserializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
      .also { it.serializerDataFormatVersion = "XYZ" }

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())

    val byteArray = stream.toByteArray()
    val deserialized = (deserializer.deserializeCache(ByteArrayInputStream(byteArray)) as? WorkspaceEntityStorageBuilderImpl)?.toStorage()

    assertNull(deserialized)
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())

    val kryo = serializer.createKryo()

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
    serializer.serializeCache(stream, builder.toStorage())
  }

  @Test
  fun `serialize abstract`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addSampleEntity("myString")

    val stream = ByteArrayOutputStream()
    val result = serializer.serializeCache(stream, builder.toStorage())

    assertTrue(result is SerializationResult.Success)
  }

  @Test
  fun `read broken cache`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder.addSampleEntity("myString")

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())

    // Remove random byte from a serialised store
    val inputStream = stream.toByteArray().filterIndexed { i, _ -> i != 3 }.toByteArray().inputStream()

    val result = serializer.deserializeCache(inputStream)

    assertNull(result)
  }

  @Test
  fun `serialize array`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()
    val infoArray = arrayOf(Info("hello"))
    builder.addEntity(ModifiableWithArrayEntity::class.java, MySource) {
      stringArrayProperty = arrayOf("1", "2", "3")
      info = infoArray
    }

    val stream = ByteArrayOutputStream()
    serializer.serializeCache(stream, builder.toStorage())

    val result = serializer.deserializeCache(stream.toByteArray().inputStream())!!

    assertArrayEquals(arrayOf("1", "2", "3"), result.entities(WithArrayEntity::class.java).single().stringArrayProperty)
    assertArrayEquals(infoArray, result.entities(WithArrayEntity::class.java).single().info)
  }
}

// Kotlin tip: Use the ugly ${'$'} to insert the $ into the multiline string
private val expectedKryoRegistration = """
  [10, com.google.common.collect.HashMultimap]
  [11, com.intellij.workspaceModel.storage.impl.ConnectionId]
  [12, com.intellij.workspaceModel.storage.impl.ImmutableEntitiesBarrel]
  [13, com.intellij.workspaceModel.storage.impl.ChildEntityId]
  [14, com.intellij.workspaceModel.storage.impl.ParentEntityId]
  [15, it.unimi.dsi.fastutil.objects.ObjectOpenHashSet]
  [16, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}TypeInfo]
  [17, java.util.ArrayList]
  [18, java.util.HashMap]
  [19, com.intellij.util.SmartList]
  [20, java.util.LinkedHashMap]
  [21, com.intellij.workspaceModel.storage.impl.containers.BidirectionalMap]
  [22, com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap]
  [23, java.util.HashSet]
  [24, com.intellij.util.containers.BidirectionalMultiMap]
  [25, com.google.common.collect.HashBiMap]
  [26, com.intellij.workspaceModel.storage.impl.containers.LinkedBidirectionalMap]
  [27, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [28, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap]
  [29, java.util.Arrays${'$'}ArrayList]
  [30, byte[]]
  [31, com.intellij.workspaceModel.storage.impl.ImmutableEntityFamily]
  [32, com.intellij.workspaceModel.storage.impl.RefsTable]
  [33, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntBiMap]
  [34, com.intellij.workspaceModel.storage.impl.containers.ImmutableIntIntUniqueBiMap]
  [35, com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex]
  [36, com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex]
  [37, com.intellij.workspaceModel.storage.impl.indices.PersistentIdInternalIndex]
  [38, com.intellij.workspaceModel.storage.impl.containers.ImmutableNonNegativeIntIntMultiMap${'$'}ByList]
  [39, int[]]
  [40, kotlin.Pair]
  [41, com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex]
  [42, com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl${'$'}SerializableEntityId]
  [43, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}AddEntity]
  [44, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [45, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [46, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [47, com.intellij.workspaceModel.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [48, java.util.LinkedHashSet]
  [49, java.util.Collections${'$'}UnmodifiableCollection]
  [50, java.util.Collections${'$'}UnmodifiableSet]
  [51, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [52, java.util.Collections${'$'}UnmodifiableMap]
  [53, java.util.Collections${'$'}EmptyList]
  [54, java.util.Collections${'$'}EmptyMap]
  [55, java.util.Collections${'$'}EmptySet]
  [56, java.util.Collections${'$'}SingletonList]
  [57, java.util.Collections${'$'}SingletonMap]
  [58, java.util.Collections${'$'}SingletonSet]
  [59, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [60, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [61, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [62, kotlin.collections.EmptyMap]
  [63, kotlin.collections.EmptyList]
  [64, kotlin.collections.EmptySet]
  [65, Object[]]
""".trimIndent()
