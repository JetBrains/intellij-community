// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.serialization.registration

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.esotericsoftware.kryo.kryo5.objenesis.instantiator.ObjectInstantiator
import com.esotericsoftware.kryo.kryo5.serializers.FieldSerializer

// TODO Dedup with OCSerializer
internal inline fun <reified T : Any> registerFieldSerializer(kryo: Kryo, type: Class<T> = T::class.java, crossinline create: () -> T) =
  registerSerializer(kryo, type, FieldSerializer(kryo, type)) { create() }

@JvmSynthetic
internal inline fun registerSingletonSerializer(kryo: Kryo, crossinline getter: () -> Any) {
  val getter1 = ObjectInstantiator { getter() }
  registerSerializer(kryo, getter1.newInstance().javaClass, EmptySerializer, getter1)
}

private fun <T : Any> registerSerializer(kryo: Kryo, type: Class<T>, serializer: Serializer<in T>, initializer: ObjectInstantiator<T>) {
  kryo.register(type, serializer).apply { instantiator = initializer }
}

private object EmptySerializer : Serializer<Any>(false, true) {
  override fun write(kryo: Kryo?, output: Output?, `object`: Any?) {}
  override fun read(kryo: Kryo, input: Input?, type: Class<out Any>?): Any = kryo.newInstance(type)
}