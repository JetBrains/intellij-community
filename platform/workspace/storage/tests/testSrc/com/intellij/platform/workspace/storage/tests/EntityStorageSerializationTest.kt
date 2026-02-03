// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.SerializationResult
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class EntityStorageSerializationTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `simple model serialization`() {
    val builder = createEmptyBuilder()
    builder addEntity SampleEntity(false, "MyEntity", ArrayList(), HashMap(), virtualFileManager.getOrCreateFromUrl("file:///tmp"),
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
                                   stringMapProperty = HashMap(), fileProperty = virtualFileManager.getOrCreateFromUrl("file:///tmp"),
                                   entitySource = SampleEntitySource("test"))

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `serialization works with kotlin buildList and other collections`() {
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
                              stringMapProperty, virtualFileManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test"))
    builder.addEntity(entity)

    val setProperty = buildSet {
      this.add(1)
      this.add(2)
      this.add(2)
    }
    builder.addEntity(CollectionFieldEntity(setProperty, listOf("one", "two", "three"), MySource))
    builder.addEntity(CollectionFieldEntity(setOf(1, 2, 3, 3, 4), listOf("one", "two", "three"), MySource))

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "")

    withTempFile { file ->
      val result = serializer.serializeCache(file, builder.toSnapshot())
      assertTrue(result is SerializationResult.Success)
    }
  }

  @Test
  fun `serialization fails on unknown lists in dataclasses`() {

    val builder = createEmptyBuilder()


    val entity = builder addEntity OneEntityWithSymbolicId("Data", MySource)
    val symbolicId = entity.symbolicId

    val weirdList = object : ArrayList<Container>() {
    }
    weirdList.add(Container(symbolicId))

    val softLinkEntity = EntityWithSoftLinks(symbolicId,
                                             listOf(symbolicId),
                                             Container(symbolicId),
                                             listOf(Container(symbolicId)),
                                             listOf(TooDeepContainer(listOf(DeepContainer(weirdList, symbolicId)))),
                                             SealedContainer.BigContainer(symbolicId),
                                             listOf(SealedContainer.SmallContainer(symbolicId)),
                                             "Hello",
                                             listOf("Hello"),
                                             DeepSealedOne.DeepSealedTwo.DeepSealedThree.DeepSealedFour(symbolicId),
                                             MySource
    )

    builder addEntity softLinkEntity

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "")

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
                              emptyMap(), virtualFileManager.getOrCreateFromUrl("file:///tmp"), SampleEntitySource("test")) {
      randomUUID = UUID.fromString("58e0a7d7-eebc-11d8-9669-0800200c9a66")
    }
    builder.addEntity(entity)

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), virtualFileManager)
  }

  @Test
  fun `serialization with version changing`() {
    val builder = createEmptyBuilder()
    builder addEntity SampleEntity(false, "MyEntity", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
                                   SampleEntitySource("test"))

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "")
    val deserializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "")
      .also { it.serializerDataFormatVersion = "XYZ" }

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())

      val deserialized = (deserializer.deserializeCache(file).getOrThrow() as? MutableEntityStorageImpl)?.toSnapshot()
      Assertions.assertNull(deserialized)
    }
  }

  @Test
  fun `serialization with ij build version changing`() {
    val builder = createEmptyBuilder()
    builder addEntity ParentEntity("Data", MySource) {
      this.child = ChildEntity("Child", MySource)
    }

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "One")
    val deserializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "Two")

    withTempFile { file ->
      serializer.serializeCache(file, builder.toSnapshot())

      val deserialized = (deserializer.deserializeCache(file).getOrThrow() as? MutableEntityStorageImpl)?.toSnapshot()
      assertNotNull(deserialized)
      assertEquals("Data", deserialized.entities<ParentEntity>().single().parentData)
    }
  }

  @Test
  fun `broken serialization without ij build version changing`() {
    val builder = createEmptyBuilder()
    builder addEntity ParentEntity("Data", MySource) {
      this.child = ChildEntity("Child", MySource)
    }

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "One")
    val deserializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "One")

    withTempFile { file ->
      builder.refs.oneToOneContainer.clear()
      assertThrows<Throwable> {
        // Check that we really broke the builder
        builder.assertConsistency()
      }
      val brokenSnapshot = builder.toSnapshot()
      serializer.serializeCache(file, brokenSnapshot)

      val cacheDeserializationResult = deserializer.deserializeCache(file)
      assertTrue(cacheDeserializationResult.isSuccess)
      val deserialized = cacheDeserializationResult.getOrThrow()
      // Check the missed issue
      assertThrows<Throwable> {
        deserialized!!.assertConsistency()
      }
    }
  }

  @Test
  fun `broken serialization with ij build version changing`() {
    val builder = createEmptyBuilder()
    builder addEntity ParentEntity("Data", MySource) {
      this.child = ChildEntity("Child", MySource)
    }

    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "One")
    val deserializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "Two")

    withTempFile { file ->
      builder.refs.oneToOneContainer.clear()
      assertThrows<Throwable> {
        // Check that we really broke the builder
        builder.assertConsistency()
      }
      val brokenSnapshot = builder.toSnapshot()
      serializer.serializeCache(file, brokenSnapshot)

      val cacheDeserializationResult = deserializer.deserializeCache(file)
      assertTrue(cacheDeserializationResult.isFailure)
    }
  }

  @Test
  fun `serializer version`() {
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "")

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, VirtualFileUrlManagerImpl(), ijBuildVersion = "")

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager, ijBuildVersion = "")

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager, ijBuildVersion = "")

    val builder = createEmptyBuilder()

    builder addEntity SampleEntity(false, "myString", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
                                   SampleEntitySource("test"))

    withTempFile { file ->
      val result = serializer.serializeCache(file, builder.toSnapshot())
      assertTrue(result is SerializationResult.Success)
    }
  }

  @Test
  fun `serialize rider like`() {
    val virtualFileManager = VirtualFileUrlManagerImpl()
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager, ijBuildVersion = "")

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
    val serializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager, ijBuildVersion = "")

    val builder = createEmptyBuilder()

    builder addEntity SampleEntity(false, "myString", ArrayList(), HashMap(), VirtualFileUrlManagerImpl().getOrCreateFromUrl("file:///tmp"),
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

    val entity = builder addEntity SampleEntity(
      false, stringProperty = "MyEntity",
      stringListProperty = mutableListOf("a", "b"),
      stringMapProperty = HashMap(), fileProperty = virtualFileManager.getOrCreateFromUrl("file:///tmp"),
      entitySource = SampleEntitySource("test")
    )

    builder addEntity CollectionFieldEntity(hashSetOf(1, 2, 3), arrayListOf("1", "2", "3"), MySource)

    builder.removeEntity(entity)

    SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
  }

  @Test
  fun `entities with sealed classes and interfaces`() {
    val builder = createEmptyBuilder()

    builder addEntity WithSealedEntity(
      listOf(MySealedClassOne("1"), MySealedClassTwo("2")),
      listOf(MySealedInterfaceOne("1"), MySealedInterfaceTwo("2")),
      MySource,
    )

    val (_, deserialized) = SerializationRoundTripChecker.verifyPSerializationRoundTrip(builder.toSnapshot(), VirtualFileUrlManagerImpl())
    val withSealedEntity = deserialized.entities<WithSealedEntity>().single()

    assertEquals("1", (withSealedEntity.classes.first() as MySealedClassOne).info)
    assertEquals("2", (withSealedEntity.classes.last() as MySealedClassTwo).info)
    assertEquals("1", (withSealedEntity.interfaces.first() as MySealedInterfaceOne).info)
    assertEquals("2", (withSealedEntity.interfaces.last() as MySealedInterfaceTwo).info)
  }
}

/**
 * Stores previously used serialization version prefixes in the [EntityStorageSerializerImpl.serializerDataFormatVersion] to check immutability
 */
private val usedCacheVersionPrefixes: List<String> = listOf("v", "version")

// Use '#' instead of '$' to separate the subclass of the class
private val expectedKryoRegistration = """
  com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
  com.google.common.collect.HashMultimap
  com.intellij.platform.workspace.storage.impl.containers.Int2IntWithDefaultMap
  com.intellij.platform.workspace.storage.ConnectionId
  com.intellij.platform.workspace.storage.impl.ImmutableEntitiesBarrel
  com.intellij.platform.workspace.storage.impl.ChildEntityId
  com.intellij.platform.workspace.storage.impl.ParentEntityId
  it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
  com.intellij.platform.workspace.storage.impl.indices.SymbolicIdInternalIndex
  com.intellij.platform.workspace.storage.impl.indices.EntityStorageInternalIndex
  com.intellij.platform.workspace.storage.impl.indices.ImmutableMultimapStorageIndex
  com.intellij.platform.workspace.storage.impl.containers.BidirectionalLongMultiMap
  it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
  com.intellij.platform.workspace.storage.impl.serialization.TypeInfo
  java.util.List
  java.util.Set
  java.util.Map
  [Ljava.lang.Object;
  kotlin.collections.ArrayDeque
  kotlin.collections.EmptyList
  kotlin.collections.EmptyMap
  kotlin.collections.EmptySet
  kotlin.collections.builders.ListBuilder
  kotlin.collections.builders.MapBuilder
  kotlin.collections.builders.SetBuilder
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
  java.util.concurrent.ConcurrentHashMap
  com.intellij.util.SmartList
  java.util.LinkedHashMap
  com.intellij.platform.workspace.storage.impl.containers.BidirectionalSetMap
  com.intellij.util.containers.BidirectionalMultiMap
  com.google.common.collect.HashBiMap
  java.util.LinkedHashSet
  com.intellij.platform.workspace.storage.impl.containers.LinkedBidirectionalMap
  it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
  kotlinx.collections.immutable.implementations.immutableMap.PersistentHashMap
  kotlinx.collections.immutable.implementations.immutableSet.PersistentHashSet
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
  com.intellij.platform.workspace.storage.ConnectionId#ConnectionType
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
