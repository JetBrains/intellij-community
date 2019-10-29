package com.intellij.workspace.api

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.DefaultSerializers
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MostlySingularMultiMap
import com.intellij.util.containers.MultiMap
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

interface EntityStorageSerializer {
  val serializerDataFormatVersion: String

  fun serializeCache(stream: OutputStream, storage: TypedEntityStorage)
  fun deserializeCache(stream: InputStream): TypedEntityStorageBuilder
}

interface EntityTypesResolver {
  fun getPluginId(clazz: Class<*>): String?
  fun resolveClass(name: String, pluginId: String?): Class<*>
}

// TODO Investigate com.esotericsoftware.kryo.ReferenceResolver for all interning
class KryoEntityStorageSerializer(private val typeResolver: EntityTypesResolver): EntityStorageSerializer {
  private val KRYO_BUFFER_SIZE = 64 * 1024

  override val serializerDataFormatVersion: String = "v1"

  private fun createKryo(): Kryo {
    val kryo = Kryo()

    kryo.isRegistrationRequired = true
    kryo.instantiatorStrategy = StdInstantiatorStrategy()

    kryo.register(VirtualFileUrl::class.java, object : Serializer<VirtualFileUrl>(false, true) {
      override fun write(kryo: Kryo, output: Output, obj: VirtualFileUrl) {
        // TODO Write IDs only
        output.writeString(obj.url)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<VirtualFileUrl>): VirtualFileUrl =
        VirtualFileUrlManager.fromUrl(input.readString())
    })

    kryo.register(TypeInfo::class.java)

    registerStandardTypes(kryo)

    return kryo
  }

  // TODO Dedup with OCSerializers
  private fun registerStandardTypes(kryo: Kryo) {
    // TODO Reuse OCSerializer.registerUtilitySerializers ?
    // TODO Scan OCSerializer for useful kryo settings and tricks
    kryo.register(java.util.ArrayList::class.java).instantiator = ObjectInstantiator { java.util.ArrayList<Any>() }

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    kryo.register(Arrays.asList("a").javaClass).instantiator = ObjectInstantiator { java.util.ArrayList<Any>() }

    kryo.register(ByteArray::class.java)

    registerFieldSerializer(kryo, Collections.unmodifiableCollection<Any>(emptySet()).javaClass) { Collections.unmodifiableCollection(emptySet()) }
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
  }

  // TODO Dedup with OCSerializer
  inline fun <reified T : Any> registerFieldSerializer(kryo: Kryo, type: Class<T> = T::class.java, crossinline create: () -> T) =
    registerFieldSerializer(kryo, type, ObjectInstantiator { create() })

  fun <T : Any> registerFieldSerializer(kryo: Kryo, type: Class<T>, initializer: ObjectInstantiator<T>) =
    registerSerializer(kryo, type, FieldSerializer(kryo, type), initializer)

  fun <T : Any> registerSerializer(kryo: Kryo, type: Class<T>, serializer: Serializer<in T>, initializer: ObjectInstantiator<T>) {
    kryo.register(type, serializer).apply { instantiator = initializer }
  }

  @JvmSynthetic
  inline fun registerSingletonSerializer(kryo: Kryo, crossinline getter: () -> Any) =
    registerSingletonSerializer(kryo, ObjectInstantiator { getter() })

  fun registerSingletonSerializer(kryo: Kryo, getter: ObjectInstantiator<Any>) =
    registerSerializer(kryo, getter.newInstance().javaClass, EmptySerializer, getter)

  object EmptySerializer : Serializer<Any>(false, true) {
    override fun write(kryo: Kryo?, output: Output?, `object`: Any?) {}
    override fun read(kryo: Kryo, input: Input?, type: Class<Any>): Any? = kryo.newInstance(type)
  }

  private fun Kryo.recursiveRegister(kind: EntityPropertyKind, metaDataRegistry: EntityMetaDataRegistry) {
    when (kind) {
      // Should be already registered
      is EntityPropertyKind.EntityReference -> Unit
      EntityPropertyKind.FileUrl -> Unit

      is EntityPropertyKind.PersistentId -> recursiveDataClass(kind.clazz, metaDataRegistry)

      is EntityPropertyKind.Primitive -> {
        if (kind.clazz.isEnum) {
          register(kind.clazz)
        }
        Unit
      }

      is EntityPropertyKind.DataClass -> {
        if (classResolver.getRegistration(kind.dataClass) == null) {
          recursiveDataClass(kind.dataClass, metaDataRegistry)
        }
        Unit
      }
      is EntityPropertyKind.List -> recursiveRegister(kind.itemKind, metaDataRegistry)
      // It's Long
      is EntityPropertyKind.EntityValue -> Unit
      is EntityPropertyKind.SealedKotlinDataClassHierarchy -> {
        kind.subclasses.forEach { subclass ->
          when {
            subclass.isData -> recursiveDataClass(subclass.java, metaDataRegistry)
            subclass.objectInstance != null -> {
              if (classResolver.getRegistration(subclass.java) == null) {
                register(subclass.java, object : Serializer<Any?>() {
                  override fun write(kryo: Kryo?, output: Output?, `object`: Any?) = Unit
                  override fun read(kryo: Kryo?, input: Input?, type: Class<Any?>?): Any? = subclass.objectInstance
                })
              }
            }
            else -> error("Unsupported subclass: $subclass")
          }

        }
      }
    }.let { }  // exhaustive when
  }

  private fun Kryo.recursiveDataClass(clazz: Class<*>, metaDataRegistry: EntityMetaDataRegistry) {
    if (classResolver.getRegistration(clazz) != null) return

    val objectInstance = clazz.kotlin.objectInstance
    if (objectInstance != null) {
      registerSingletonSerializer(this) { objectInstance }
    }
    else {
      register(clazz)
      val metadata = metaDataRegistry.getDataClassMetaData(clazz)
      metadata.properties.values.forEach { recursiveRegister(it, metaDataRegistry) }
    }
  }

  private fun Kryo.registerClasses(entityClasses: List<Class<out TypedEntity>>, entitySourceClasses: List<Class<*>>, metaDataRegistry: EntityMetaDataRegistry) {
    for (clazz in entityClasses) {
      register(clazz)
      val metadata = metaDataRegistry.getEntityMetaData(clazz)
      metadata.properties.values.forEach { recursiveRegister(it, metaDataRegistry) }
    }

    for (clazz in entitySourceClasses) {
      recursiveDataClass(clazz, metaDataRegistry)
    }
  }

  override fun serializeCache(stream: OutputStream, storage: TypedEntityStorage) {
    storage as ProxyBasedEntityStorage

    val output = Output(stream, KRYO_BUFFER_SIZE)
    try {
      val allEntityClasses = storage.entitiesByType.keys.toList()
      val allEntitySourceClasses = storage.entitiesBySource.keys.map { it.javaClass }.distinct()

      val kryo = createKryo()
      kryo.registerClasses(allEntityClasses, allEntitySourceClasses, storage.metaDataRegistry)

      output.writeVarInt(allEntityClasses.size, true)
      for (entityClass in allEntityClasses) {
        val hash = storage.metaDataRegistry.getEntityMetaData(entityClass).hash(storage.metaDataRegistry)
        kryo.writeObject(output, TypeInfo(entityClass.name, typeResolver.getPluginId(entityClass), hash))
      }

      output.writeVarInt(allEntitySourceClasses.size, true)
      for (sourceClass in allEntitySourceClasses) {
        val hash = storage.metaDataRegistry.getDataClassMetaData(sourceClass).hash(storage.metaDataRegistry)
        kryo.writeObject(output, TypeInfo(sourceClass.name, typeResolver.getPluginId(sourceClass), hash))
      }

      val sources = storage.entitiesBySource.keys.toList()

      // sources
      output.writeVarInt(sources.size, true)
      for (source in sources) {
        kryo.writeClassAndObject(output, source)
      }
      val sourcesMap = sources.mapIndexed { index, entitySource -> entitySource to index }.toMap()

      // entities
      val entities = storage.entityById.values.toList()
      output.writeVarInt(entities.size, true)
      for (data in entities) {
        output.writeVarLong(data.id, true)

        output.writeVarInt(sourcesMap.getValue(data.entitySource), true)
        kryo.writeClass(output, data.unmodifiableEntityType)

        // TODO non-recursive yet
        // TODO Use metadata
        output.writeVarInt(data.properties.size, true)
        for ((propertyName, propertyValue) in data.properties) {
          output.writeString(propertyName)
          kryo.writeClassAndObject(output, propertyValue)
        }
      }
    } finally {
      output.flush()
    }
  }

  override fun deserializeCache(stream: InputStream): TypedEntityStorageBuilder {
    Input(stream, KRYO_BUFFER_SIZE).use { input ->
      val kryo = createKryo()

      val metaDataRegistry = EntityMetaDataRegistry()

      fun readTypes(): List<TypeInfo> {
        val size = input.readVarInt(true)
        val all = ArrayList<TypeInfo>(size)
        repeat(size) { all.add(kryo.readObject(input, TypeInfo::class.java)) }
        return all
      }

      val allEntityClasses = readTypes().map { typeInfo ->
        @Suppress("UNCHECKED_CAST")
        val clazz = typeResolver.resolveClass(typeInfo.name, typeInfo.pluginId) as Class<out TypedEntity>
        if (!TypedEntity::class.java.isAssignableFrom(clazz)) {
          error("Class is not inherited from TypedEntity: $clazz")
        }

        val expectedHash = typeInfo.hash
        val actualHash = metaDataRegistry.getEntityMetaData(clazz).hash(metaDataRegistry)
        if (!expectedHash.contentEquals(actualHash)) {
          error("Serialized entity type and current runtime type are different: $clazz")
        }

        clazz
      }

      val allEntitySourceClasses = readTypes().map { typeInfo ->
        @Suppress("UNCHECKED_CAST")
        val clazz = typeResolver.resolveClass(typeInfo.name, typeInfo.pluginId) as Class<out EntitySource>
        if (!EntitySource::class.java.isAssignableFrom(clazz)) {
          error("Class is not inherited from EntitySource: $clazz")
        }

        val expectedHash = typeInfo.hash
        val actualHash = metaDataRegistry.getDataClassMetaData(clazz).hash(metaDataRegistry)
        if (!expectedHash.contentEquals(actualHash)) {
          error("Serialized entity source type and current runtime type are different: $clazz")
        }

        clazz
      }

      kryo.registerClasses(allEntityClasses, allEntitySourceClasses, metaDataRegistry)

      fun readSources(): List<EntitySource> {
        val size = input.readVarInt(true)

        val all = ArrayList<EntitySource>(size)
        for (i in 0 until size) {
          all.add(kryo.readClassAndObject(input) as EntitySource)
        }

        return all
      }

      val sources = readSources()

      val builder = TypedEntityStorageBuilder.create() as TypedEntityStorageBuilderImpl

      val entitiesCount = input.readVarInt(true)
      for (i in 0 until entitiesCount) {
        val id = input.readVarLong(true)
        val entitySource = sources[input.readVarInt(true)]

        @Suppress("UNCHECKED_CAST")
        val entityType = kryo.readClass(input).type as Class<TypedEntity>

        val propertiesCount = input.readVarInt(true)

        val properties = mutableMapOf<String, Any?>()
        repeat(propertiesCount) {
          // TODO intern
          val name = input.readString()

          val value = kryo.readClassAndObject(input)

          properties[name] = value
        }

        builder.addEntity(
          entityData = EntityData(
            entitySource = entitySource,
            id = id,
            metaData = metaDataRegistry.getEntityMetaData(entityType),
            properties = properties
          ),
          entityInstance = null,
          handleReferrers = true
        )
      }

      return builder
    }
  }

  private class TypeInfo(val name: String, val pluginId: String?, val hash: ByteArray)
}