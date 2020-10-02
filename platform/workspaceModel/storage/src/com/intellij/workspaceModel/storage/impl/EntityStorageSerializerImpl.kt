// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.DefaultSerializers
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SmartList
import com.intellij.util.containers.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.containers.*
import com.intellij.workspaceModel.storage.impl.containers.BidirectionalSetMap
import com.intellij.workspaceModel.storage.impl.containers.LinkedBidirectionalMap
import com.intellij.workspaceModel.storage.impl.indices.EntityStorageInternalIndex
import com.intellij.workspaceModel.storage.impl.indices.MultimapStorageIndex
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.HashMap
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.jvmName

class EntityStorageSerializerImpl(private val typesResolver: EntityTypesResolver,
                                  private val virtualFileManager: VirtualFileUrlManager,
                                  private val registrationRequired: Boolean) : EntityStorageSerializer {
  private val KRYO_BUFFER_SIZE = 64 * 1024

  @set:TestOnly
  override var serializerDataFormatVersion: String = "v2"

  private fun createKryo(): Kryo {
    val kryo = Kryo()

    kryo.isRegistrationRequired = registrationRequired
    kryo.instantiatorStrategy = StdInstantiatorStrategy()

    kryo.register(VirtualFileUrl::class.java, object : Serializer<VirtualFileUrl>(false, true) {
      override fun write(kryo: Kryo, output: Output, obj: VirtualFileUrl) {
        // TODO Write IDs only
        output.writeString(obj.url)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<VirtualFileUrl>): VirtualFileUrl =
        virtualFileManager.fromUrl(input.readString())
    })

    kryo.register(EntityId::class.java, object : Serializer<EntityId>(false, true) {
      override fun write(kryo: Kryo, output: Output, `object`: EntityId) {
        output.writeInt(`object`.arrayId)
        val typeClass = `object`.clazz.findEntityClass<WorkspaceEntity>()
        val typeInfo = TypeInfo(typeClass.name, typesResolver.getPluginId(typeClass))
        kryo.writeClassAndObject(output, typeInfo)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<EntityId>): EntityId {
        val arrayId = input.readInt()
        val clazzInfo = kryo.readClassAndObject(input) as TypeInfo
        val clazz = typesResolver.resolveClass(clazzInfo.name, clazzInfo.pluginId)
        return EntityId(arrayId, clazz.toClassId())
      }
    })

    kryo.register(HashMultimap::class.java, object : Serializer<HashMultimap<*, *>>(false, true) {
      override fun write(kryo: Kryo, output: Output, `object`: HashMultimap<*, *>) {
        val res = HashMap<Any, Collection<Any>>()
        `object`.asMap().forEach { (key, values) ->
          res[key] = ArrayList(values)
        }
        kryo.writeClassAndObject(output, res)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<HashMultimap<*, *>>): HashMultimap<*, *> {
        val res = HashMultimap.create<Any, Any>()
        val map = kryo.readClassAndObject(input) as HashMap<*, Collection<*>>
        map.forEach { (key, values) ->
          res.putAll(key, values)
        }
        return res
      }
    })

    kryo.register(ConnectionId::class.java, object : Serializer<ConnectionId>(false, true) {
      override fun write(kryo: Kryo, output: Output, `object`: ConnectionId) {
        val parentClassType = `object`.parentClass.findEntityClass<WorkspaceEntity>()
        val childClassType = `object`.childClass.findEntityClass<WorkspaceEntity>()
        val parentTypeInfo = TypeInfo(parentClassType.name, typesResolver.getPluginId(parentClassType))
        val childTypeInfo = TypeInfo(childClassType.name, typesResolver.getPluginId(childClassType))

        kryo.writeClassAndObject(output, parentTypeInfo)
        kryo.writeClassAndObject(output, childTypeInfo)
        output.writeString(`object`.connectionType.name)
        output.writeBoolean(`object`.isParentNullable)
        output.writeBoolean(`object`.isChildNullable)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<ConnectionId>): ConnectionId {
        val parentClazzInfo = kryo.readClassAndObject(input) as TypeInfo
        val childClazzInfo = kryo.readClassAndObject(input) as TypeInfo

        val parentClass = typesResolver.resolveClass(parentClazzInfo.name, parentClazzInfo.pluginId) as Class<WorkspaceEntity>
        val childClass = typesResolver.resolveClass(childClazzInfo.name, childClazzInfo.pluginId) as Class<WorkspaceEntity>

        val connectionType = ConnectionId.ConnectionType.valueOf(input.readString())
        val parentNullable = input.readBoolean()
        val childNullable = input.readBoolean()
        return ConnectionId.create(parentClass, childClass, connectionType, parentNullable, childNullable)
      }
    })

    kryo.register(ImmutableEntitiesBarrel::class.java, object : Serializer<ImmutableEntitiesBarrel>(false, true) {
      override fun write(kryo: Kryo, output: Output, `object`: ImmutableEntitiesBarrel) {
        val res = HashMap<TypeInfo, EntityFamily<*>>()
        `object`.entityFamilies.forEachIndexed { i, v ->
          if (v == null) return@forEachIndexed
          val clazz = i.findEntityClass<WorkspaceEntity>()
          val typeInfo = TypeInfo(clazz.name, typesResolver.getPluginId(clazz))
          res[typeInfo] = v
        }
        kryo.writeClassAndObject(output, res)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<ImmutableEntitiesBarrel>): ImmutableEntitiesBarrel {
        val mutableBarrel = MutableEntitiesBarrel.create()
        val families = kryo.readClassAndObject(input) as HashMap<TypeInfo, EntityFamily<*>>
        for ((typeInfo, family) in families) {
          val classId = typesResolver.resolveClass(typeInfo.name, typeInfo.pluginId).toClassId()
          mutableBarrel.fillEmptyFamilies(classId)
          mutableBarrel.entityFamilies[classId] = family
        }
        return mutableBarrel.toImmutable()
      }
    })

    kryo.register(TypeInfo::class.java)

    // TODO Dedup with OCSerializers
    // TODO Reuse OCSerializer.registerUtilitySerializers ?
    // TODO Scan OCSerializer for useful kryo settings and tricks
    kryo.register(java.util.ArrayList::class.java).instantiator = ObjectInstantiator { ArrayList<Any>() }
    kryo.register(HashMap::class.java).instantiator = ObjectInstantiator { HashMap<Any, Any>() }
    kryo.register(SmartList::class.java).instantiator = ObjectInstantiator { SmartList<Any>() }
    kryo.register(LinkedHashMap::class.java).instantiator = ObjectInstantiator { LinkedHashMap<Any, Any>() }
    kryo.register(BidirectionalMap::class.java).instantiator = ObjectInstantiator { BidirectionalMap<Any, Any>() }
    kryo.register(BidirectionalSetMap::class.java).instantiator = ObjectInstantiator { BidirectionalSetMap<Any, Any>() }
    kryo.register(HashSet::class.java).instantiator = ObjectInstantiator { HashSet<Any>() }
    kryo.register(BidirectionalMultiMap::class.java).instantiator = ObjectInstantiator { BidirectionalMultiMap<Any, Any>() }
    kryo.register(HashBiMap::class.java).instantiator = ObjectInstantiator { HashBiMap.create<Any, Any>() }
    kryo.register(LinkedBidirectionalMap::class.java).instantiator = ObjectInstantiator { LinkedBidirectionalMap<Any, Any>() }
    kryo.register(Int2IntOpenHashMap::class.java).instantiator = ObjectInstantiator { Int2IntOpenHashMap() }

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    kryo.register(Arrays.asList("a").javaClass).instantiator = ObjectInstantiator { java.util.ArrayList<Any>() }

    kryo.register(ByteArray::class.java)
    kryo.register(ImmutableEntityFamily::class.java)
    kryo.register(RefsTable::class.java)
    kryo.register(ImmutableNonNegativeIntIntBiMap::class.java)
    kryo.register(ImmutableIntIntUniqueBiMap::class.java)
    kryo.register(VirtualFileIndex::class.java)
    kryo.register(EntityStorageInternalIndex::class.java)
    kryo.register(ImmutableNonNegativeIntIntMultiMap.ByList::class.java)
    kryo.register(IntArray::class.java)
    kryo.register(Pair::class.java)
    kryo.register(MultimapStorageIndex::class.java)
    kryo.register(VirtualFileIndex.VirtualFileUrlInfo::class.java)

    kryo.register(WorkspaceEntityStorageBuilderImpl.ChangeEntry.AddEntity::class.java)
    kryo.register(WorkspaceEntityStorageBuilderImpl.ChangeEntry.RemoveEntity::class.java)
    kryo.register(WorkspaceEntityStorageBuilderImpl.ChangeEntry.ReplaceEntity::class.java)
    kryo.register(WorkspaceEntityStorageBuilderImpl.ChangeEntry.ChangeEntitySource::class.java)
    kryo.register(LinkedHashSet::class.java)

    registerFieldSerializer(kryo, Collections.unmodifiableCollection<Any>(emptySet()).javaClass) {
      Collections.unmodifiableCollection(emptySet())
    }
    registerFieldSerializer(kryo, Collections.unmodifiableSet<Any>(emptySet()).javaClass) { Collections.unmodifiableSet(emptySet()) }
    registerFieldSerializer(kryo, Collections.unmodifiableList<Any>(emptyList()).javaClass) { Collections.unmodifiableList(emptyList()) }
    registerFieldSerializer(kryo, Collections.unmodifiableMap<Any, Any>(emptyMap()).javaClass) { Collections.unmodifiableMap(emptyMap()) }

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

    return kryo
  }

  // TODO Dedup with OCSerializer
  private inline fun <reified T : Any> registerFieldSerializer(kryo: Kryo, type: Class<T> = T::class.java, crossinline create: () -> T) =
    registerSerializer(kryo, type, FieldSerializer(kryo, type), ObjectInstantiator { create() })

  @JvmSynthetic
  private inline fun registerSingletonSerializer(kryo: Kryo, crossinline getter: () -> Any) {
    val getter1 = ObjectInstantiator { getter() }
    registerSerializer(kryo, getter1.newInstance().javaClass, EmptySerializer, getter1)
  }

  object EmptySerializer : Serializer<Any>(false, true) {
    override fun write(kryo: Kryo?, output: Output?, `object`: Any?) {}
    override fun read(kryo: Kryo, input: Input?, type: Class<Any>): Any? = kryo.newInstance(type)
  }

  private fun <T : Any> registerSerializer(kryo: Kryo, type: Class<T>, serializer: Serializer<in T>, initializer: ObjectInstantiator<T>) {
    kryo.register(type, serializer).apply { instantiator = initializer }
  }

  /**
   * Collect all classes existing in entity data.
   * [simpleClasses] - set of classes
   * [objectClasses] - set of kotlin objects
   */
  private fun recursiveClassFinder(kryo: Kryo, entity: Any, simpleClasses: MutableSet<TypeInfo>, objectClasses: MutableSet<TypeInfo>) {
    val kClass = entity::class
    if (registerKClass(kClass, kryo, objectClasses, simpleClasses)) return

    kClass.memberProperties.forEach {
      val retType = (it.returnType as Any).toString()

      if ((retType.startsWith("kotlin") || retType.startsWith("java"))
          && !retType.startsWith("kotlin.collections.List")
          && !retType.startsWith("java.util.List")
      ) return@forEach

      if (it.visibility != KVisibility.PUBLIC) return@forEach
      val property = it.getter.call(entity) ?: run {
        registerKClass(it.returnType.jvmErasure, kryo, objectClasses, simpleClasses)
        return@forEach
      }
      recursiveClassFinder(kryo, property, simpleClasses, objectClasses)

      if (property is List<*>) {
        property.filterNotNull().forEach { listItem ->
          recursiveClassFinder(kryo, listItem, simpleClasses, objectClasses)
        }
      }
    }
  }

  private fun registerKClass(kClass: KClass<out Any>,
                             kryo: Kryo,
                             objectClasses: MutableSet<TypeInfo>,
                             simpleClasses: MutableSet<TypeInfo>): Boolean {
    val typeInfo = TypeInfo(kClass.jvmName, typesResolver.getPluginId(kClass.java))
    if (kryo.classResolver.getRegistration(kClass.java) != null) return true

    val objectInstance = kClass.objectInstance
    if (objectInstance != null) {
      objectClasses += typeInfo
    }
    else {
      simpleClasses += typeInfo
    }
    return false
  }

  override fun serializeCache(stream: OutputStream, storage: WorkspaceEntityStorage) {
    storage as WorkspaceEntityStorageImpl

    val output = Output(stream, KRYO_BUFFER_SIZE)
    try {
      val kryo = createKryo()

      // Save version
      output.writeString(serializerDataFormatVersion)

      val entityDataSequence = storage.entitiesByType.entityFamilies.filterNotNull().asSequence().flatMap { family ->
        family.entities.asSequence().filterNotNull()
      }
      collectAndRegisterClasses(kryo, output, entityDataSequence)

      // Serialize and register persistent ids
      val persistentIds = storage.indexes.persistentIdIndex.entries().toSet()
      output.writeVarInt(persistentIds.size, true)
      persistentIds.forEach {
        val typeInfo = TypeInfo(it::class.jvmName, typesResolver.getPluginId(it::class.java))
        kryo.register(it::class.java)
        kryo.writeClassAndObject(output, typeInfo)
      }

      // Write entity data and references
      kryo.writeClassAndObject(output, storage.entitiesByType)
      kryo.writeClassAndObject(output, storage.refs)

      // Write indexes
      kryo.writeClassAndObject(output, storage.indexes.softLinks)

      kryo.writeClassAndObject(output, storage.indexes.virtualFileIndex.entityId2VirtualFileUrlInfo)
      kryo.writeClassAndObject(output, storage.indexes.virtualFileIndex.vfu2VirtualFileUrlInfo)

      kryo.writeClassAndObject(output, storage.indexes.entitySourceIndex)
      kryo.writeClassAndObject(output, storage.indexes.persistentIdIndex)
    }
    finally {
      output.flush()
    }
  }

  internal fun serializeDiffLog(stream: OutputStream, changeLog: List<WorkspaceEntityStorageBuilderImpl.ChangeEntry>) {
    val output = Output(stream, KRYO_BUFFER_SIZE)
    try {
      val kryo = createKryo()

      // Save version
      output.writeString(serializerDataFormatVersion)

      val entityDataSequence = changeLog.mapNotNull {
        when (it) {
          is WorkspaceEntityStorageBuilderImpl.ChangeEntry.AddEntity<*> -> it.entityData
          is WorkspaceEntityStorageBuilderImpl.ChangeEntry.RemoveEntity -> null
          is WorkspaceEntityStorageBuilderImpl.ChangeEntry.ReplaceEntity<*> -> it.newData
          is WorkspaceEntityStorageBuilderImpl.ChangeEntry.ChangeEntitySource<*> -> it.newData
        }
      }.asSequence()

      collectAndRegisterClasses(kryo, output, entityDataSequence)

      kryo.writeClassAndObject(output, changeLog)
    }
    finally {
      output.flush()
    }
  }

  fun serializeClassToIntConverter(stream: OutputStream) {
    val converterMap = ClassToIntConverter.getMap().toMap()
    val output = Output(stream, KRYO_BUFFER_SIZE)
    try {
      val kryo = createKryo()

      // Save version
      output.writeString(serializerDataFormatVersion)

      val mapData = converterMap.map { (key, value) -> TypeInfo(key.name, typesResolver.getPluginId(key)) to value }

      kryo.writeClassAndObject(output, mapData)
    }
    finally {
      output.flush()
    }
  }

  private fun collectAndRegisterClasses(kryo: Kryo,
                                        output: Output,
                                        entityDataSequence: Sequence<WorkspaceEntityData<*>>) {
    // Collect all classes existing in entity data
    val simpleClasses = HashSet<TypeInfo>()
    val objectClasses = HashSet<TypeInfo>()
    entityDataSequence.forEach { recursiveClassFinder(kryo, it, simpleClasses, objectClasses) }

    // Serialize and register types of kotlin objects
    output.writeVarInt(objectClasses.size, true)
    objectClasses.forEach {
      kryo.register(typesResolver.resolveClass(it.name, it.pluginId))
      kryo.writeClassAndObject(output, it)
    }

    // Serialize and register all types existing in entity data
    output.writeVarInt(simpleClasses.size, true)
    simpleClasses.forEach {
      kryo.register(typesResolver.resolveClass(it.name, it.pluginId))
      kryo.writeClassAndObject(output, it)
    }
  }

  override fun deserializeCache(stream: InputStream): WorkspaceEntityStorageBuilder? {
    Input(stream, KRYO_BUFFER_SIZE).use { input ->
      val kryo = createKryo()

      // Read version
      val cacheVersion = input.readString()
      if (cacheVersion != serializerDataFormatVersion) {
        logger.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
        return null
      }

      readAndRegisterClasses(input, kryo)

      // Read and register persistent ids
      val persistentIdCount = input.readVarInt(true)
      repeat(persistentIdCount) {
        val objectClass = kryo.readClassAndObject(input) as TypeInfo
        kryo.register(typesResolver.resolveClass(objectClass.name, objectClass.pluginId))
      }

      // Read entity data and references
      val entitiesBarrel = kryo.readClassAndObject(input) as ImmutableEntitiesBarrel
      val refsTable = kryo.readClassAndObject(input) as RefsTable

      // Read indexes
      val softLinks = kryo.readClassAndObject(input) as MultimapStorageIndex

      val entityId2VirtualFileUrlInfo = kryo.readClassAndObject(input) as HashMap<EntityId, MutableSet<VirtualFileIndex.VirtualFileUrlInfo>>
      val vfu2VirtualFileUrlInfo = kryo.readClassAndObject(input) as HashMap<VirtualFileUrl, MutableSet<VirtualFileIndex.VirtualFileUrlInfo>>
      val virtualFileIndex = VirtualFileIndex(entityId2VirtualFileUrlInfo, vfu2VirtualFileUrlInfo)

      val entitySourceIndex = kryo.readClassAndObject(input) as EntityStorageInternalIndex<EntitySource>
      val persistentIdIndex = kryo.readClassAndObject(input) as EntityStorageInternalIndex<PersistentEntityId<*>>
      val storageIndexes = StorageIndexes(softLinks, virtualFileIndex, entitySourceIndex, persistentIdIndex)


      val storage = WorkspaceEntityStorageImpl(entitiesBarrel, refsTable, storageIndexes)
      storage.assertConsistencyInStrictMode()
      val builder = WorkspaceEntityStorageBuilderImpl.from(storage)

      builder.entitiesByType.entityFamilies.forEach { family ->
        family?.entities?.asSequence()?.filterNotNull()?.forEach { entityData -> builder.createAddEvent(entityData) }
      }

      return builder
    }
  }

  private fun readAndRegisterClasses(input: Input, kryo: Kryo) {
    // Read and register all kotlin objects
    val objectCount = input.readVarInt(true)
    repeat(objectCount) {
      val objectClass = kryo.readClassAndObject(input) as TypeInfo
      registerSingletonSerializer(kryo) { typesResolver.resolveClass(objectClass.name, objectClass.pluginId).kotlin.objectInstance!! }
    }

    // Read and register all types in entity data
    val nonObjectCount = input.readVarInt(true)
    repeat(nonObjectCount) {
      val objectClass = kryo.readClassAndObject(input) as TypeInfo
      kryo.register(typesResolver.resolveClass(objectClass.name, objectClass.pluginId))
    }
  }

  fun deserializeCacheAndDiffLog(storeStream: InputStream, diffLogStream: InputStream): WorkspaceEntityStorageBuilder? {
    val builder = this.deserializeCache(storeStream) ?: return null

    var log: List<WorkspaceEntityStorageBuilderImpl.ChangeEntry>
    Input(diffLogStream, KRYO_BUFFER_SIZE).use { input ->
      val kryo = createKryo()

      // Read version
      val cacheVersion = input.readString()
      if (cacheVersion != serializerDataFormatVersion) {
        logger.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
        return null
      }

      readAndRegisterClasses(input, kryo)

      log = kryo.readClassAndObject(input) as List<WorkspaceEntityStorageBuilderImpl.ChangeEntry>
    }

    builder as WorkspaceEntityStorageBuilderImpl
    builder.changeLogImpl.clear()
    builder.changeLogImpl.addAll(log)

    return builder
  }

  fun deserializeClassToIntConverter(stream: InputStream) {
    Input(stream, KRYO_BUFFER_SIZE).use { input ->
      val kryo = createKryo()

      // Read version
      val cacheVersion = input.readString()
      if (cacheVersion != serializerDataFormatVersion) {
        logger.info("Cache isn't loaded. Current version of cache: $serializerDataFormatVersion, version of cache file: $cacheVersion")
        return
      }

      val classes = kryo.readClassAndObject(input) as List<Pair<TypeInfo, Int>>

      val map = classes.map { (first, second) -> typesResolver.resolveClass(first.name, first.pluginId) to second }.toMap()
      ClassToIntConverter.fromMap(map)
    }
  }

  private data class TypeInfo(val name: String, val pluginId: String?)

  companion object {
    val logger = logger<EntityStorageSerializerImpl>()
  }
}
