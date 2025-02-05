// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.serializer

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Registration
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.esotericsoftware.kryo.kryo5.serializers.CollectionSerializer
import com.esotericsoftware.kryo.kryo5.serializers.MapSerializer
import com.google.common.collect.HashMultimap
import com.intellij.platform.workspace.storage.impl.containers.Int2IntWithDefaultMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentHashSet


internal class DefaultListSerializer<T : List<*>> : CollectionSerializer<T>() {
  override fun create(kryo: Kryo, input: Input, type: Class<out T>, size: Int): T {
    val registration: Registration = kryo.getRegistration(type)
    if (registration.instantiator == null && List::class.java.isAssignableFrom(type)) {
      @Suppress("UNCHECKED_CAST")
      return ArrayList<Any>(size) as T
    }
    return super.create(kryo, input, type, size)
  }
}


internal class DefaultSetSerializer<T : Set<*>> : CollectionSerializer<T>() {
  override fun create(kryo: Kryo, input: Input, type: Class<out T>, size: Int): T {
    val registration: Registration = kryo.getRegistration(type)
    if (registration.instantiator == null && Set::class.java.isAssignableFrom(type)) {
      @Suppress("UNCHECKED_CAST")
      return HashSet<Any>(size) as T
    }
    return super.create(kryo, input, type, size)
  }
}


internal class DefaultMapSerializer<T : Map<*, *>> : MapSerializer<T>() {
  override fun create(kryo: Kryo, input: Input, type: Class<out T>, size: Int): T {
    val registration = kryo.getRegistration(type)
    if (registration.instantiator == null && Map::class.java.isAssignableFrom(type)) {
      @Suppress("UNCHECKED_CAST")
      return HashMap<Any, Any>(size) as T
    }
    return super.create(kryo, input, type, size)
  }
}


internal class PersistentHashMapSerializer : Serializer<PersistentMap<*, *>>(false, true) {
  override fun write(kryo: Kryo, output: Output, `object`: PersistentMap<*, *>) {
    val res = `object`.toMap()
    kryo.writeClassAndObject(output, res)
  }

  override fun read(kryo: Kryo, input: Input, type: Class<out PersistentMap<*, *>>): PersistentMap<*, *> {
    val des = kryo.readClassAndObject(input) as Map<*, *>
    val res = des.toPersistentHashMap()
    return res
  }
}

internal class PersistentHashSetSerializer : Serializer<PersistentSet<*>>(false, true) {
  override fun write(kryo: Kryo, output: Output, `object`: PersistentSet<*>) {
    val res = `object`.toSet()
    kryo.writeClassAndObject(output, res)
  }

  override fun read(kryo: Kryo, input: Input, type: Class<out PersistentSet<*>>): PersistentSet<*> {
    val des = kryo.readClassAndObject(input) as Set<*>
    val res = des.toPersistentHashSet()
    return res
  }
}


internal class ObjectOpenHashSetSerializer : Serializer<ObjectOpenHashSet<*>>(false, true) {
  override fun write(kryo: Kryo, output: Output, `object`: ObjectOpenHashSet<*>) {
    output.writeInt(`object`.size)
    `object`.forEach { kryo.writeClassAndObject(output, it) }
  }

  override fun read(kryo: Kryo, input: Input, type: Class<out ObjectOpenHashSet<*>>): ObjectOpenHashSet<*> {
    @Suppress("SSBasedInspection")
    val res = ObjectOpenHashSet<Any>()
    repeat(input.readInt()) {
      val data = kryo.readClassAndObject(input)
      res.add(data)
    }
    return res
  }
}


internal class HashMultimapSerializer : Serializer<HashMultimap<*, *>>(false, true) {
  override fun write(kryo: Kryo, output: Output, `object`: HashMultimap<*, *>) {
    val res = HashMap<Any?, Collection<Any?>>()
    `object`.asMap().forEach { (key, values) ->
      res[key] = ArrayList(values)
    }
    kryo.writeClassAndObject(output, res)
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(kryo: Kryo, input: Input, type: Class<out HashMultimap<*, *>>): HashMultimap<*, *> {
    val res = HashMultimap.create<Any, Any>()
    val map = kryo.readClassAndObject(input) as HashMap<*, Collection<*>>
    map.forEach { (key, values) ->
      res.putAll(key, values)
    }
    return res
  }
}

internal class Int2IntWithDefaultMapSerializer : Serializer<Int2IntWithDefaultMap>(false, true) {
  override fun write(kryo: Kryo, output: Output, `object`: Int2IntWithDefaultMap) {
    val res = `object`.backingMap.toMap()
    kryo.writeClassAndObject(output, res)
  }

  @Suppress("UNCHECKED_CAST")
  override fun read(kryo: Kryo, input: Input, type: Class<out Int2IntWithDefaultMap>): Int2IntWithDefaultMap {
    val des = kryo.readClassAndObject(input) as Map<Int, Int>
    val res = Int2IntWithDefaultMap()
    des.forEach { key, value ->
      res.put(key, value)
    }
    return res
  }
}

/**
 * Int2IntOpenHashMap is prohibited in EntityStorage because it's error-prone.
 *   It's very easy to create an instance of Int2IntOpenHashMap and forget to set the default value (see IDEA-338250)
 * Use [Int2IntWithDefaultMap] if you need an Int2IntMap with -1 as default value or create another wrapper.
 */
internal class Int2IntOpenHashMapSerializer : Serializer<Int2IntOpenHashMap>(false, true) {
  override fun write(kryo: Kryo?, output: Output?, `object`: Int2IntOpenHashMap?) {
    error("Int2IntOpenHashMap is prohibited in EntityStorage")
  }

  override fun read(kryo: Kryo?, input: Input?, type: Class<out Int2IntOpenHashMap>?): Int2IntOpenHashMap {
    error("Int2IntOpenHashMap is prohibited in EntityStorage")
  }
}
