// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.SerializationResult
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver
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

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl())

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

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl())
    val deserializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl())
      .also { it.serializerDataFormatVersion = "XYZ" }

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())

      val deserialized = (deserializer.deserializeCache(file).getOrThrow() as? MutableEntityStorageImpl)?.toSnapshot()
      Assertions.assertNull(deserialized)
    }
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl())

    val (kryo, _) = serializer.createKryo()

    val registration = (10..1_000).mapNotNull { kryo.getRegistration(it) }.joinToString(separator = "\n")

    assertEquals(expectedKryoRegistration, registration,
                            "Have you changed kryo registration? Update the version number! (And this test)")
  }

  @Test
  fun `serialize empty lists`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager)

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager)

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager)

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager)

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
  [25, Object[]]
  [26, kotlin.collections.ArrayDeque]
  [27, kotlin.collections.EmptyList]
  [28, kotlin.collections.EmptyMap]
  [29, kotlin.collections.EmptySet]
  [30, java.util.ArrayList]
  [31, java.util.LinkedList]
  [32, java.util.Stack]
  [33, java.util.Vector]
  [34, java.util.HashSet]
  [35, java.util.EnumSet]
  [36, java.util.TreeSet]
  [37, java.util.HashMap]
  [38, java.util.TreeMap]
  [39, java.util.EnumMap]
  [40, java.util.Hashtable]
  [41, java.util.WeakHashMap]
  [42, java.util.IdentityHashMap]
  [43, com.intellij.util.SmartList]
  [44, java.util.LinkedHashMap]
  [45, com.intellij.platform.workspace.storage.impl.containers.BidirectionalMap]
  [46, com.intellij.platform.workspace.storage.impl.containers.BidirectionalSetMap]
  [47, com.intellij.util.containers.BidirectionalMultiMap]
  [48, com.google.common.collect.HashBiMap]
  [49, java.util.LinkedHashSet]
  [50, com.intellij.platform.workspace.storage.impl.containers.LinkedBidirectionalMap]
  [51, it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap]
  [52, it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap]
  [53, com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList]
  [54, com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet]
  [55, java.util.Arrays${'$'}ArrayList]
  [56, byte[]]
  [57, com.intellij.platform.workspace.storage.impl.ImmutableEntityFamily]
  [58, com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex]
  [59, int[]]
  [60, kotlin.Pair]
  [61, com.intellij.platform.workspace.storage.impl.serialization.SerializableEntityId]
  [62, com.intellij.platform.workspace.storage.impl.RefsTable]
  [63, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToOneContainer]
  [64, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToManyContainer]
  [65, com.intellij.platform.workspace.storage.impl.references.ImmutableAbstractOneToOneContainer]
  [66, com.intellij.platform.workspace.storage.impl.references.ImmutableOneToAbstractManyContainer]
  [67, com.intellij.platform.workspace.storage.impl.containers.MutableIntIntUniqueBiMap]
  [68, com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntBiMap]
  [69, com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntMultiMap${'$'}ByList]
  [70, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}AddEntity]
  [71, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}RemoveEntity]
  [72, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity]
  [73, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ChangeEntitySource]
  [74, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceAndChangeSource]
  [75, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}Data]
  [76, com.intellij.platform.workspace.storage.impl.ChangeEntry${'$'}ReplaceEntity${'$'}References]
  [77, com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata]
  [78, com.intellij.platform.workspace.storage.metadata.model.EntityMetadata]
  [79, com.intellij.platform.workspace.storage.metadata.model.StorageClassMetadata]
  [80, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata]
  [81, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}ClassMetadata]
  [82, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}ObjectMetadata]
  [83, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}EnumClassMetadata]
  [84, com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata${'$'}KnownClass]
  [85, com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata]
  [86, com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata${'$'}AbstractClassMetadata]
  [87, com.intellij.platform.workspace.storage.metadata.model.PropertyMetadata]
  [88, com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata]
  [89, com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata]
  [90, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata]
  [91, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}ParameterizedType]
  [92, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}SimpleType]
  [93, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}SimpleType${'$'}PrimitiveType]
  [94, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}SimpleType${'$'}CustomType]
  [95, com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata${'$'}EntityReference]
  [96, com.intellij.platform.workspace.storage.impl.ConnectionId${'$'}ConnectionType]
  [97, com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata]
  [98, java.util.Collections${'$'}UnmodifiableCollection]
  [99, java.util.Collections${'$'}UnmodifiableSet]
  [100, java.util.Collections${'$'}UnmodifiableRandomAccessList]
  [101, java.util.Collections${'$'}UnmodifiableMap]
  [102, java.util.Collections${'$'}EmptyList]
  [103, java.util.Collections${'$'}EmptyMap]
  [104, java.util.Collections${'$'}EmptySet]
  [105, java.util.Collections${'$'}SingletonList]
  [106, java.util.Collections${'$'}SingletonMap]
  [107, java.util.Collections${'$'}SingletonSet]
  [108, com.intellij.util.containers.ContainerUtilRt${'$'}EmptyList]
  [109, com.intellij.util.containers.MostlySingularMultiMap${'$'}EmptyMap]
  [110, com.intellij.util.containers.MultiMap${'$'}EmptyMap]
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
