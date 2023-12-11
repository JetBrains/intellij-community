// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.SerializationResult
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    val registration = (10..1_000)
      .mapNotNull {
        kryo.getRegistration(it)?.type?.name?.replace("$",
                                                      "#") // '$' is not convenient to be saved in expected string, so let's replace it with '#'
      }
      .joinToString(separator = "\n")

    assertEquals(expectedKryoRegistration, registration,
                 """
                   |Have you changed kryo registration? Update the version number! (And this test)
                   |Existing result:
                   |=========
                   |
                   |$registration
                   |
                   |=========
                 """.trimMargin())
  }

  @Test
  fun `immutable serializer version prefix`() {
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl())

    val serializationVersionNumber = "[1-9][0-9]*".toRegex().find(serializer.serializerDataFormatVersion)?.value
    val serializationVersionPrefix = serializationVersionNumber?.let {
      serializer.serializerDataFormatVersion.substringBefore(serializationVersionNumber)
    }

    assertEquals(usedCacheVersionPrefixes.last(), serializationVersionPrefix,
                 "Have you changed serialization version prefix? Add the new prefix to the usedCacheVersionPrefixes list!")

    assertContentEquals(usedCacheVersionPrefixes, usedCacheVersionPrefixes.distinct(),
                        "Version prefix of the cache ${serializationVersionPrefix} was used previously. Add the new version prefix!")
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

/**
 * Stores previously used serialization version prefixes in the [EntityStorageSerializerImpl.serializerDataFormatVersion] to check immutability
 */
private val usedCacheVersionPrefixes: List<String> = listOf("v", "version")

// Use '#' instead of '$' to separate the subclass of the class
private val expectedKryoRegistration = """
  com.google.common.collect.HashMultimap
  com.intellij.platform.workspace.storage.impl.containers.Int2IntWithDefaultMap
  com.intellij.platform.workspace.storage.impl.ConnectionId
  com.intellij.platform.workspace.storage.impl.ImmutableEntitiesBarrel
  com.intellij.platform.workspace.storage.impl.ChildEntityId
  com.intellij.platform.workspace.storage.impl.ParentEntityId
  it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
  com.intellij.platform.workspace.storage.impl.indices.SymbolicIdInternalIndex
  com.intellij.platform.workspace.storage.impl.indices.EntityStorageInternalIndex
  com.intellij.platform.workspace.storage.impl.indices.MultimapStorageIndex
  com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap
  it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
  it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
  com.intellij.platform.workspace.storage.impl.serialization.TypeInfo
  java.util.List
  java.util.Set
  java.util.Map
  [Ljava.lang.Object;
  kotlin.collections.ArrayDeque
  kotlin.collections.EmptyList
  kotlin.collections.EmptyMap
  kotlin.collections.EmptySet
  java.util.ArrayList
  java.util.LinkedList
  java.util.Stack
  java.util.Vector
  java.util.HashSet
  java.util.EnumSet
  java.util.TreeSet
  java.util.HashMap
  java.util.TreeMap
  java.util.EnumMap
  java.util.Hashtable
  java.util.WeakHashMap
  java.util.IdentityHashMap
  com.intellij.util.SmartList
  java.util.LinkedHashMap
  com.intellij.platform.workspace.storage.impl.containers.BidirectionalSetMap
  com.intellij.util.containers.BidirectionalMultiMap
  com.google.common.collect.HashBiMap
  java.util.LinkedHashSet
  com.intellij.platform.workspace.storage.impl.containers.LinkedBidirectionalMap
  it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
  com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
  com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
  java.util.Arrays#ArrayList
  [B
  com.intellij.platform.workspace.storage.impl.ImmutableEntityFamily
  com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
  [I
  kotlin.Pair
  com.intellij.platform.workspace.storage.impl.serialization.SerializableEntityId
  com.intellij.platform.workspace.storage.impl.RefsTable
  com.intellij.platform.workspace.storage.impl.references.ImmutableOneToOneContainer
  com.intellij.platform.workspace.storage.impl.references.ImmutableOneToManyContainer
  com.intellij.platform.workspace.storage.impl.references.ImmutableAbstractOneToOneContainer
  com.intellij.platform.workspace.storage.impl.references.ImmutableOneToAbstractManyContainer
  com.intellij.platform.workspace.storage.impl.containers.MutableIntIntUniqueBiMap
  com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntBiMap
  com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntMultiMap#ByList
  com.intellij.platform.workspace.storage.impl.ChangeEntry#AddEntity
  com.intellij.platform.workspace.storage.impl.ChangeEntry#RemoveEntity
  com.intellij.platform.workspace.storage.impl.ChangeEntry#ReplaceEntity
  com.intellij.platform.workspace.storage.impl.ChangeEntry#ReplaceEntity#Data
  com.intellij.platform.workspace.storage.impl.ChangeEntry#ReplaceEntity#References
  com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata
  com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata#Id
  com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata#SerializableTypeMetadata
  com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
  com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
  com.intellij.platform.workspace.storage.metadata.model.StorageClassMetadata
  com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
  com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata#ClassMetadata
  com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata#ObjectMetadata
  com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata#EnumClassMetadata
  com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata#KnownClass
  com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
  com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata#AbstractClassMetadata
  com.intellij.platform.workspace.storage.metadata.model.PropertyMetadata
  com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
  com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
  com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata
  com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata#ParameterizedType
  com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata#SimpleType
  com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata#SimpleType#PrimitiveType
  com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata#SimpleType#CustomType
  com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata#EntityReference
  com.intellij.platform.workspace.storage.impl.ConnectionId#ConnectionType
  java.util.Collections#UnmodifiableCollection
  java.util.Collections#UnmodifiableSet
  java.util.Collections#UnmodifiableRandomAccessList
  java.util.Collections#UnmodifiableMap
  java.util.Collections#EmptyList
  java.util.Collections#EmptyMap
  java.util.Collections#EmptySet
  java.util.Collections#SingletonList
  java.util.Collections#SingletonMap
  java.util.Collections#SingletonSet
  com.intellij.util.containers.ContainerUtilRt#EmptyList
  com.intellij.util.containers.MostlySingularMultiMap#EmptyMap
  com.intellij.util.containers.MultiMap#EmptyMap
  java.util.UUID
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
