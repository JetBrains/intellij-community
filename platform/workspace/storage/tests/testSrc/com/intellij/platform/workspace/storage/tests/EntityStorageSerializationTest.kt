// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.SerializationResult
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EntityStorageSerializationTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
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
  fun `serialization failed because of unsupported entity collections and maps`() {
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

    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())

    withTempFile { file ->
      val result = serializer.serializeCache(file, builder.toSnapshot())
      assertFalse(result is SerializationResult.Success)
    }
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
      Assertions.assertNull(deserialized)
    }
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())

    val (kryo, _) = serializer.createKryo()

    val registration = (10..1_000).mapNotNull { kryo.getRegistration(it) }.joinToString(separator = "\n")

    assertEquals(expectedKryoRegistration, registration,
                            "Have you changed kryo registration? Update the version number! (And this test)")
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
      Assertions.assertNull(result.getOrNull())
    }
  }

  @Test
  fun `empty entity family serialization`() {
    val builder = createEmptyBuilder()

    val entity = SampleEntity(
      false, stringProperty = "MyEntity",
      stringListProperty = mutableListOf("a", "b"),
      stringMapProperty = HashMap(), fileProperty = virtualFileManager.fromUrl("file:///tmp"),
      entitySource = SampleEntitySource("test")
    )

    builder addEntity entity

    builder addEntity CollectionFieldEntity(hashSetOf(1, 2, 3), arrayListOf("1", "2", "3"), MySource)

    builder.removeEntity(entity)

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
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
  [21, com.intellij.platform.workspace.storage.impl.serialization.TypeInfo]
  [22, java.util.List]
  [23, java.util.Set]
  [24, java.util.Map]
  [25, java.util.ArrayList]
  [26, java.util.LinkedList]
  [27, kotlin.collections.ArrayDeque]
  [28, java.util.Stack]
  [29, java.util.Vector]
  [30, java.util.HashSet]
  [31, java.util.EnumSet]
  [32, java.util.TreeSet]
  [33, java.util.HashMap]
  [34, java.util.TreeMap]
  [35, java.util.EnumMap]
  [36, java.util.Hashtable]
  [37, java.util.WeakHashMap]
  [38, java.util.IdentityHashMap]
  [39, com.intellij.util.SmartList]
  [40, java.util.LinkedHashMap]
  [41, com.intellij.platform.workspace.storage.impl.containers.BidirectionalMap]
  [42, com.intellij.platform.workspace.storage.impl.containers.BidirectionalSetMap]
  [43, com.intellij.util.containers.BidirectionalMultiMap]
  [44, com.google.common.collect.HashBiMap]
  [45, java.util.LinkedHashSet]
  [46, com.intellij.platform.workspace.storage.impl.containers.LinkedBidirectionalMap]
  [47, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [48, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap]
  [49, com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList]
  [50, com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet]
  [51, java.util.Arrays${'$'}ArrayList]
  [52, byte[]]
  [53, com.intellij.platform.workspace.storage.impl.ImmutableEntityFamily]
  [54, com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex]
  [55, int[]]
  [56, kotlin.Pair]
  [57, com.intellij.platform.workspace.storage.impl.serialization.SerializableEntityId]
  [58, com.intellij.platform.workspace.storage.impl.RefsTable]
  [59, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToOneContainer]
  [60, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToManyContainer]
  [61, com.intellij.platform.workspace.storage.impl.references.ImmutableAbstractOneToOneContainer]
  [62, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToAbstractManyContainer]
  [63, com.intellij.platform.workspace.storage.impl.containers.MutableIntIntUniqueBiMap]
  [64, com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntBiMap]
  [65, com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntMultiMap${'$'}ByList]
  [66, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}AddEntity]
  [67, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [68, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [69, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [70, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [71, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}Data]
  [72, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}References]
  [73, com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata]
  [74, com.intellij.platform.workspace.storage.metadata.model.EntityMetadata]
  [75, com.intellij.platform.workspace.storage.metadata.model.StorageClassMetadata]
  [76, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata]
  [77, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}ClassMetadata]
  [78, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}ObjectMetadata]
  [79, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}EnumClassMetadata]
  [80, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}KnownClass]
  [81, com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata]
  [82, com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata${'$'}AbstractClassMetadata]
  [83, com.intellij.platform.workspace.storage.metadata.model.PropertyMetadata]
  [84, com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata]
  [85, com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata]
  [86, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata]
  [87, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}ParameterizedType]
  [88, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}SimpleType]
  [89, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}SimpleType${'$'}PrimitiveType]
  [90, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}SimpleType${'$'}CustomType]
  [91, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}EntityReference]
  [92, com.intellij.platform.workspace.storage.impl.ConnectionId${'$'}ConnectionType]
  [93, com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata]
  [94, java.util.Collections${'$'}UnmodifiableCollection]
  [95, java.util.Collections${'$'}UnmodifiableSet]
  [96, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [97, java.util.Collections${'$'}UnmodifiableMap]
  [98, java.util.Collections${'$'}EmptyList]
  [99, java.util.Collections${'$'}EmptyMap]
  [100, java.util.Collections${'$'}EmptySet]
  [101, java.util.Collections${'$'}SingletonList]
  [102, java.util.Collections${'$'}SingletonMap]
  [103, java.util.Collections${'$'}SingletonSet]
  [104, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [105, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [106, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
  [107, kotlin.collections.EmptyMap]
  [108, kotlin.collections.EmptyList]
  [109, kotlin.collections.EmptySet]
  [110, Object[]]
  [111, com.intellij.platform.workspace.storage.impl.AnonymizedEntitySource]
  [112, com.intellij.platform.workspace.storage.impl.MatchedEntitySource]
  [113, com.intellij.platform.workspace.storage.impl.UnmatchedEntitySource]
  [114, java.util.UUID]
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
