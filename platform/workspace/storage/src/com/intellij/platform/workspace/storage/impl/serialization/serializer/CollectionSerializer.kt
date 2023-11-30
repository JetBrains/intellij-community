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
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet


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