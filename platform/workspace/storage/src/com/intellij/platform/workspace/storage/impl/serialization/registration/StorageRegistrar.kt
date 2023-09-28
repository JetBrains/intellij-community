// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.registration

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.objenesis.instantiator.ObjectInstantiator
import com.esotericsoftware.kryo.kryo5.serializers.DefaultSerializers
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.platform.workspace.storage.EntityTypesResolver
import com.intellij.util.SmartList
import com.intellij.util.containers.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.ChildEntityId
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.ImmutableEntitiesBarrel
import com.intellij.platform.workspace.storage.impl.ImmutableEntityFamily
import com.intellij.platform.workspace.storage.impl.ParentEntityId
import com.intellij.platform.workspace.storage.impl.RefsTable
import com.intellij.platform.workspace.storage.impl.containers.*
import com.intellij.platform.workspace.storage.impl.containers.BidirectionalMap
import com.intellij.platform.workspace.storage.impl.indices.*
import com.intellij.platform.workspace.storage.impl.references.ImmutableAbstractOneToOneContainer
import com.intellij.platform.workspace.storage.impl.references.ImmutableOneToAbstractManyContainer
import com.intellij.platform.workspace.storage.impl.references.ImmutableOneToManyContainer
import com.intellij.platform.workspace.storage.impl.references.ImmutableOneToOneContainer
import com.intellij.platform.workspace.storage.impl.serialization.CacheMetadata
import com.intellij.platform.workspace.storage.impl.serialization.SerializableEntityId
import com.intellij.platform.workspace.storage.impl.serialization.TypeInfo
import com.intellij.platform.workspace.storage.impl.serialization.serializer.*
import com.intellij.platform.workspace.storage.impl.serialization.serializer.HashMultimapSerializer
import com.intellij.platform.workspace.storage.impl.serialization.serializer.ObjectOpenHashSetSerializer
import com.intellij.platform.workspace.storage.metadata.model.*
import com.intellij.platform.workspace.storage.metadata.model.PropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.*
import java.util.*
import java.util.Stack
import kotlin.collections.ArrayDeque
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet

internal interface StorageRegistrar {
  fun registerClasses(kryo: Kryo)
}

internal class StorageClassesRegistrar(
  private val serializerUtil: StorageSerializerUtil,
  private val typesResolver: EntityTypesResolver
): StorageRegistrar {

  private val kotlinPluginId = "org.jetbrains.kotlin"

  private val kotlinCollectionsToRegistrar: List<Class<*>> = listOf(
    ArrayDeque::class.java, emptyList<Any>()::class.java, emptyMap<Any, Any>()::class.java, emptySet<Any>()::class.java
  )

  override fun registerClasses(kryo: Kryo) {
    registerDefaultSerializers(kryo)

    kryo.register(EntityId::class.java, serializerUtil.getEntityIdSerializer())
    kryo.register(HashMultimap::class.java, HashMultimapSerializer())
    kryo.register(ConnectionId::class.java, serializerUtil.getConnectionIdSerializer())
    kryo.register(ImmutableEntitiesBarrel::class.java, serializerUtil.getImmutableEntitiesBarrelSerializer())
    kryo.register(ChildEntityId::class.java, serializerUtil.getChildEntityIdSerializer())
    kryo.register(ParentEntityId::class.java, serializerUtil.getParentEntityIdSerializer())
    kryo.register(ObjectOpenHashSet::class.java, ObjectOpenHashSetSerializer())
    kryo.register(SymbolicIdInternalIndex::class.java, serializerUtil.getSymbolicIdIndexSerializer())
    kryo.register(EntityStorageInternalIndex::class.java, serializerUtil.getEntityStorageIndexSerializer())
    kryo.register(MultimapStorageIndex::class.java, serializerUtil.getMultimapStorageIndexSerializer())
    kryo.register(BidirectionalLongMultiMap::class.java, serializerUtil.getEntityId2JarDirSerializer())
    kryo.register(Object2ObjectOpenCustomHashMap::class.java, serializerUtil.getVfu2EntityIdSerializer())
    kryo.register(Long2ObjectOpenHashMap::class.java, serializerUtil.getEntityId2VfuSerializer())

    kryo.register(TypeInfo::class.java)

    registerCollections(kryo)

    kryo.register(ByteArray::class.java)
    kryo.register(ImmutableEntityFamily::class.java)
    kryo.register(VirtualFileIndex::class.java)
    kryo.register(EntityStorageInternalIndex::class.java)
    kryo.register(SymbolicIdInternalIndex::class.java)
    kryo.register(IntArray::class.java)
    kryo.register(Pair::class.java)
    kryo.register(MultimapStorageIndex::class.java)
    kryo.register(SerializableEntityId::class.java)

    registerRefsTableClasses(kryo)

    registerChangeEntry(kryo)

    registerMetadataClasses(kryo)

    registerFieldSerializers(kryo)

    registerEmptyCollections(kryo)

    registerAnonymizedEntitySources(kryo)

    kryo.register(UUID::class.java)
  }

  private fun registerDefaultSerializers(kryo: Kryo) {
    kryo.addDefaultSerializer(VirtualFileUrl::class.java, serializerUtil.getVirtualFileUrlSerializer())
    kryo.addDefaultSerializer(List::class.java, DefaultListSerializer::class.java)
    kryo.addDefaultSerializer(Set::class.java, DefaultSetSerializer::class.java)
    kryo.addDefaultSerializer(Map::class.java, DefaultMapSerializer::class.java)
  }

  // TODO Dedup with OCSerializers
  // TODO Reuse OCSerializer.registerUtilitySerializers ?
  // TODO Scan OCSerializer for useful kryo settings and tricks
  private fun registerCollections(kryo: Kryo) {
    kryo.register(List::class.java)
    kryo.register(Set::class.java)
    kryo.register(Map::class.java)

    kryo.register(MutableList::class.java)
    kryo.register(MutableSet::class.java)
    kryo.register(MutableMap::class.java)

    kryo.register(Array::class.java)

    registerKotlinCollections(kryo)

    registerKotlinCollectionsInKotlinPlugin(kryo)

    kryo.register(ArrayList::class.java)
    kryo.register(LinkedList::class.java)
    kryo.register(Stack::class.java)
    kryo.register(Vector::class.java)

    kryo.register(HashSet::class.java)
    kryo.register(EnumSet::class.java)
    kryo.register(TreeSet::class.java)

    kryo.register(HashMap::class.java)
    kryo.register(TreeMap::class.java)
    kryo.register(EnumMap::class.java)
    kryo.register(Hashtable::class.java)
    kryo.register(WeakHashMap::class.java)
    kryo.register(IdentityHashMap::class.java)

    kryo.register(SmartList::class.java).instantiator = ObjectInstantiator { SmartList<Any>() }
    kryo.register(LinkedHashMap::class.java).instantiator = ObjectInstantiator { LinkedHashMap<Any, Any>() }
    kryo.register(BidirectionalMap::class.java).instantiator = ObjectInstantiator { BidirectionalMap<Any, Any>() }
    kryo.register(BidirectionalSetMap::class.java).instantiator = ObjectInstantiator { BidirectionalSetMap<Any, Any>() }
    kryo.register(BidirectionalMultiMap::class.java).instantiator = ObjectInstantiator { BidirectionalMultiMap<Any, Any>() }
    kryo.register(HashBiMap::class.java).instantiator = ObjectInstantiator { HashBiMap.create<Any, Any>() }
    kryo.register(LinkedHashSet::class.java).instantiator = ObjectInstantiator { LinkedHashSet<Any>() }
    kryo.register(LinkedBidirectionalMap::class.java).instantiator = ObjectInstantiator { LinkedBidirectionalMap<Any, Any>() }
    kryo.register(Int2IntOpenHashMap::class.java).instantiator = ObjectInstantiator { Int2IntOpenHashMap() }
    @Suppress("SSBasedInspection")
    kryo.register(ObjectOpenHashSet::class.java).instantiator = ObjectInstantiator { ObjectOpenHashSet<Any>() }
    @Suppress("SSBasedInspection")
    kryo.register(Object2ObjectOpenHashMap::class.java).instantiator = ObjectInstantiator { Object2ObjectOpenHashMap<Any, Any>() }

    kryo.register(MutableWorkspaceList::class.java)
    kryo.register(MutableWorkspaceSet::class.java)
    //register java.util.Arrays.ArrayList
    kryo.register(Arrays.asList<Int>()::class.java)
  }

  private fun registerKotlinCollections(kryo: Kryo) {
    kotlinCollectionsToRegistrar.forEach { kryo.register(it) }
  }

  private fun registerKotlinCollectionsInKotlinPlugin(kryo: Kryo) {
    val classLoader = typesResolver.getClassLoader(kotlinPluginId)
    if (classLoader != null) {
      kotlinCollectionsToRegistrar.forEach {
        val classInKotlinPlugin = classLoader.loadClass(it.name)
        kryo.register(classInKotlinPlugin)
      }
    }
  }

  private fun registerRefsTableClasses(kryo: Kryo) {
    kryo.register(RefsTable::class.java)
    kryo.register(ImmutableOneToOneContainer::class.java)
    kryo.register(ImmutableOneToManyContainer::class.java)
    kryo.register(ImmutableAbstractOneToOneContainer::class.java)
    kryo.register(ImmutableOneToAbstractManyContainer::class.java)
    kryo.register(MutableIntIntUniqueBiMap::class.java)
    kryo.register(MutableNonNegativeIntIntBiMap::class.java)
    kryo.register(MutableNonNegativeIntIntMultiMap.ByList::class.java)
  }

  private fun registerChangeEntry(kryo: Kryo) {
    kryo.register(ChangeEntry.AddEntity::class.java)
    kryo.register(ChangeEntry.RemoveEntity::class.java)
    kryo.register(ChangeEntry.ReplaceEntity::class.java)
    kryo.register(ChangeEntry.ChangeEntitySource::class.java)
    kryo.register(ChangeEntry.ReplaceAndChangeSource::class.java)
    kryo.register(ChangeEntry.ReplaceEntity.Data::class.java)
    kryo.register(ChangeEntry.ReplaceEntity.References::class.java)
  }

  private fun registerMetadataClasses(kryo: Kryo) {
    kryo.register(StorageTypeMetadata::class.java)

    kryo.register(EntityMetadata::class.java)

    kryo.register(StorageClassMetadata::class.java)
    kryo.register(FinalClassMetadata::class.java)
    kryo.register(FinalClassMetadata.ClassMetadata::class.java)
    kryo.register(FinalClassMetadata.ObjectMetadata::class.java)
    kryo.register(FinalClassMetadata.EnumClassMetadata::class.java)
    kryo.register(FinalClassMetadata.KnownClass::class.java)
    kryo.register(ExtendableClassMetadata::class.java)
    kryo.register(ExtendableClassMetadata.AbstractClassMetadata::class.java)

    kryo.register(PropertyMetadata::class.java)
    kryo.register(OwnPropertyMetadata::class.java)
    kryo.register(ExtPropertyMetadata::class.java)

    kryo.register(ValueTypeMetadata::class.java)
    kryo.register(ParameterizedType::class.java)
    kryo.register(SimpleType::class.java)
    kryo.register(SimpleType.PrimitiveType::class.java)
    kryo.register(SimpleType.CustomType::class.java)
    kryo.register(EntityReference::class.java)
    kryo.register(ConnectionId.ConnectionType::class.java)

    kryo.register(CacheMetadata::class.java)
  }

  private fun registerFieldSerializers(kryo: Kryo) {
    registerFieldSerializer(kryo, Collections.unmodifiableCollection<Any>(emptySet()).javaClass) {
      Collections.unmodifiableCollection(emptySet())
    }
    registerFieldSerializer(kryo, Collections.unmodifiableSet<Any>(emptySet()).javaClass) { Collections.unmodifiableSet(emptySet()) }
    registerFieldSerializer(kryo, Collections.unmodifiableList<Any>(emptyList()).javaClass) { Collections.unmodifiableList(emptyList()) }
    registerFieldSerializer(kryo, Collections.unmodifiableMap<Any, Any>(emptyMap()).javaClass) { Collections.unmodifiableMap(emptyMap()) }
  }

  private fun registerEmptyCollections(kryo: Kryo) {
    kryo.register(Collections.EMPTY_LIST.javaClass, DefaultSerializers.CollectionsEmptyListSerializer())
    kryo.register(Collections.EMPTY_MAP.javaClass, DefaultSerializers.CollectionsEmptyMapSerializer())
    kryo.register(Collections.EMPTY_SET.javaClass, DefaultSerializers.CollectionsEmptySetSerializer())
    kryo.register(listOf(null).javaClass, DefaultSerializers.CollectionsSingletonListSerializer())
    kryo.register(Collections.singletonMap<Any, Any>(null, null).javaClass, DefaultSerializers.CollectionsSingletonMapSerializer())
    kryo.register(setOf(null).javaClass, DefaultSerializers.CollectionsSingletonSetSerializer())

    registerSingletonSerializer(kryo) { ContainerUtil.emptyList<Any>() }
    registerSingletonSerializer(kryo) { MostlySingularMultiMap.emptyMap<Any, Any>() }
    registerSingletonSerializer(kryo) { MultiMap.empty<Any, Any>() }

    registerSingletonSerializer(kryo) { emptyMap<Any, Any>() }
    registerSingletonSerializer(kryo) { emptyList<Any>() }
    registerSingletonSerializer(kryo) { emptySet<Any>() }
    registerSingletonSerializer(kryo) { emptyArray<Any>() }
  }

  private fun registerAnonymizedEntitySources(kryo: Kryo) {
    kryo.register(AnonymizedEntitySource::class.java)
    kryo.register(MatchedEntitySource::class.java)
    kryo.register(UnmatchedEntitySource::class.java)
  }
}