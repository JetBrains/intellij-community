// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.DefaultSerializers
import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.google.common.collect.HashBiMap
import com.google.common.collect.HashMultimap
import com.intellij.util.containers.*
import com.intellij.workspace.api.*
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.StdInstantiatorStrategy
import pstorage.containers.LinkedBidirectionalMap
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PSerializer(private val virtualFileManager: VirtualFileUrlManager) : EntityStorageSerializer {
  private val KRYO_BUFFER_SIZE = 64 * 1024
  override val serializerDataFormatVersion: String = "v1"

  private fun createKryo(): Kryo {
    val kryo = Kryo()

    //kryo.isRegistrationRequired = true
    kryo.instantiatorStrategy = StdInstantiatorStrategy()

    kryo.register(VirtualFileUrl::class.java, object : Serializer<VirtualFileUrl>(false, true) {
      override fun write(kryo: Kryo, output: Output, obj: VirtualFileUrl) {
        // TODO Write IDs only
        output.writeString(obj.url)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<VirtualFileUrl>): VirtualFileUrl =
        virtualFileManager.fromUrl(input.readString())
    })

    kryo.register(PId::class.java, object : Serializer<PId<*>>(false, true) {
      override fun write(kryo: Kryo, output: Output, `object`: PId<*>) {
        output.writeInt(`object`.arrayId)
        kryo.writeClass(output, `object`.clazz.java)
      }

      override fun read(kryo: Kryo, input: Input, type: Class<PId<*>>): PId<*> {
        val arrayId = input.readInt()
        val clazz = kryo.readClass(input).type.kotlin as KClass<TypedEntity>
        return PId(arrayId, clazz)
      }
    })

    kryo.register(TypeInfo::class.java)

    registerStandardTypes(kryo)

    return kryo
  }

  // TODO Dedup with OCSerializers
  private fun registerStandardTypes(kryo: Kryo) {
    // TODO Reuse OCSerializer.registerUtilitySerializers ?
    // TODO Scan OCSerializer for useful kryo settings and tricks
    kryo.register(java.util.ArrayList::class.java).instantiator = ObjectInstantiator { ArrayList<Any>() }
    kryo.register(HashMap::class.java).instantiator = ObjectInstantiator { HashMap<Any, Any>() }
    kryo.register(LinkedHashMap::class.java).instantiator = ObjectInstantiator { LinkedHashMap<Any, Any>() }
    kryo.register(BidirectionalMap::class.java).instantiator = ObjectInstantiator { BidirectionalMap<Any, Any>() }
    kryo.register(HashSet::class.java).instantiator = ObjectInstantiator { HashSet<Any>() }
    kryo.register(BidirectionalMultiMap::class.java).instantiator = ObjectInstantiator { BidirectionalMultiMap<Any, Any>() }
    kryo.register(HashBiMap::class.java).instantiator = ObjectInstantiator { HashBiMap.create<Any, Any>() }
    kryo.register(LinkedBidirectionalMap::class.java).instantiator = ObjectInstantiator { LinkedBidirectionalMap<Any, Any>() }
    kryo.register(HashMultimap::class.java).instantiator = ObjectInstantiator { HashMultimap.create<Any, Any>() }

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    kryo.register(Arrays.asList("a").javaClass).instantiator = ObjectInstantiator { java.util.ArrayList<Any>() }

    kryo.register(ByteArray::class.java)

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
    registerSerializer(kryo, getter.newInstance().javaClass, KryoEntityStorageSerializer.EmptySerializer, getter)

  private fun recursiveSingletons(kryo: Kryo, entity: Any, track: MutableSet<KClass<*>>) {
    if (entity::class in track) return

    val objectInstance = entity::class.objectInstance
    if (objectInstance != null) {
      registerSingletonSerializer(kryo) { objectInstance }
    }
    track += entity::class

    entity::class.memberProperties.forEach {
      val retType = (it.returnType as Any).toString()
      if (retType.startsWith("kotlin") || retType.startsWith("java")) return@forEach
      if (it.visibility != KVisibility.PUBLIC) return@forEach
      val property = it.getter.call(entity) ?: return@forEach
      recursiveSingletons(kryo, property, track)
    }
  }

  private fun findSingletons(kryo: Kryo, storage: PEntityStorage) {
    val track = HashSet<KClass<*>>()
    storage.entitiesByType.all().asSequence().flatMap { it.value.all() }.forEach {
      recursiveSingletons(kryo, it, track)
    }
  }

  override fun serializeCache(stream: OutputStream, storage: TypedEntityStorage) {
    storage as PEntityStorage

    val output = Output(stream, KRYO_BUFFER_SIZE)
    try {
      val kryo = createKryo()

      //findSingletons(kryo, storage)
      kryo.writeClassAndObject(output, storage)
    }
    finally {
      output.flush()
    }
  }

  override fun deserializeCache(stream: InputStream): TypedEntityStorageBuilder {
    Input(stream, KRYO_BUFFER_SIZE).use { input ->
      val kryo = createKryo()

      val storage = kryo.readClassAndObject(input) as PEntityStorage
      return PEntityStorageBuilder.from(storage)
    }
  }

  private class TypeInfo(val name: String, val pluginId: String?, val hash: ByteArray)
}