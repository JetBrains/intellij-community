// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.SerializationResult
import com.intellij.platform.workspace.storage.impl.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class EntityStorageSerializationTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `simple model serialization`() {
    val builder = createEmptyBuilder()
    builder addEntity SampleEntity(false, "MyEntity", ArrayList(), HashMap(), virtualFileManager.fromUrl("file:///tmp"),
                                   SampleEntitySource("test"))

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `entity properties serialization`() {
    val builder = createEmptyBuilder()
    mutableSetOf("c", "d")
    VirtualFileUrlManagerImpl()
    builder addEntity SampleEntity(false, stringProperty = "MyEntity",
                                   stringListProperty = mutableListOf("a", "b"),
                                   stringMapProperty = HashMap(), fileProperty = virtualFileManager.fromUrl("file:///tmp"),
                                   entitySource = SampleEntitySource("test"))

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
    builder.addEntity(CollectionFieldEntity(setOf(1, 2, 3, 3, 4), listOf("one", "two", "three"), MySource))

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
    builder addEntity SampleEntity(false, "MyEntity", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                   SampleEntitySource("test"))

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
    builder addEntity CollectionFieldEntity(emptySet(), ArrayList(), MySource)

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())
    }
  }

  @Test
  fun `serialize abstract`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), virtualFileManager)

    val builder = createEmptyBuilder()

    builder addEntity SampleEntity(false, "myString", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                   SampleEntitySource("test"))

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

    builder addEntity SampleEntity(false, "myString", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                   SampleEntitySource("test"))

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
  [10, com.intellij.platform.workspace.storage.impl.ConnectionId]
  [11, com.intellij.platform.workspace.storage.impl.ImmutableEntitiesBarrel]
  [12, com.intellij.platform.workspace.storage.impl.ChildEntityId]
  [13, com.intellij.platform.workspace.storage.impl.ParentEntityId]
  [14, it.unimi.dsi.fastutil.objects.ObjectOpenHashSet]
  [15, com.intellij.platform.workspace.storage.impl.indices.SymbolicIdInternalIndex]
  [16, com.intellij.platform.workspace.storage.impl.indices.EntityStorageInternalIndex]
  [17, com.intellij.platform.workspace.storage.impl.indices.MultimapStorageIndex]
  [18, com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap]
  [19, it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap]
  [20, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap]
  [21, com.intellij.platform.workspace.storage.impl.EntityStorageSerializerImpl${'$'}TypeInfo]
  [22, java.util.List]
  [23, java.util.ArrayList]
  [24, java.util.HashMap]
  [25, com.intellij.util.SmartList]
  [26, java.util.LinkedHashMap]
  [27, com.intellij.platform.workspace.storage.impl.containers.BidirectionalMap]
  [28, com.intellij.platform.workspace.storage.impl.containers.BidirectionalSetMap]
  [29, com.intellij.util.containers.BidirectionalMultiMap]
  [30, com.google.common.collect.HashBiMap]
  [31, java.util.LinkedHashSet]
  [32, com.intellij.platform.workspace.storage.impl.containers.LinkedBidirectionalMap]
  [33, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [34, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap]
  [35, byte[]]
  [36, com.intellij.platform.workspace.storage.impl.ImmutableEntityFamily]
  [37, com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex]
  [38, int[]]
  [39, kotlin.Pair]
  [40, com.intellij.platform.workspace.storage.impl.EntityStorageSerializerImpl${'$'}SerializableEntityId]
  [41, com.intellij.platform.workspace.storage.impl.RefsTable]
  [42, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToOneContainer]
  [43, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToManyContainer]
  [44, com.intellij.platform.workspace.storage.impl.references.ImmutableAbstractOneToOneContainer]
  [45, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToAbstractManyContainer]
  [46, com.intellij.platform.workspace.storage.impl.containers.MutableIntIntUniqueBiMap]
  [47, com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntBiMap]
  [48, com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntMultiMap${'$'}ByList]
  [49, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}AddEntity]
  [50, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [51, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [52, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [53, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [54, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}Data]
  [55, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}References]
  [56, java.util.Collections${'$'}UnmodifiableCollection]
  [57, java.util.Collections${'$'}UnmodifiableSet]
  [58, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [59, java.util.Collections${'$'}UnmodifiableMap]
  [60, java.util.Collections${'$'}EmptyList]
  [61, java.util.Collections${'$'}EmptyMap]
  [62, java.util.Collections${'$'}EmptySet]
  [63, java.util.Collections${'$'}SingletonList]
  [64, java.util.Collections${'$'}SingletonMap]
  [65, java.util.Collections${'$'}SingletonSet]
  [66, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [67, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [68, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [69, kotlin.collections.EmptyMap]
  [70, kotlin.collections.EmptyList]
  [71, kotlin.collections.EmptySet]
  [72, Object[]]
  [73, java.util.UUID]
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
