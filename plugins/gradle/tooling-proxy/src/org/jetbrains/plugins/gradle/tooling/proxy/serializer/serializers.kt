// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.proxy.serializer

import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.esotericsoftware.kryo.kryo5.objenesis.strategy.StdInstantiatorStrategy
import com.esotericsoftware.kryo.kryo5.serializers.DefaultSerializers
import java.io.File
import java.util.Collections
import java.util.EnumMap
import java.util.EnumSet
import java.util.IdentityHashMap
import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet

internal fun getKryo(classLoader: ClassLoader): Kryo {
  val kryo = Kryo()

  kryo.isRegistrationRequired = true
  kryo.references = true
  kryo.setCopyReferences(true)
  kryo.instantiatorStrategy = StdInstantiatorStrategy()
  kryo.classLoader = classLoader

  kryo.registerJavaStdlibClasses()
  kryo.registerJavaCollectionClasses()

  return kryo
}

internal fun Kryo.registerJavaStdlibClasses() {
  register(File::class.java, KryoFileSerializer)
}

internal fun Kryo.registerJavaCollectionClasses() {
  register(List::class.java)
  register(Set::class.java)
  register(Map::class.java)

  register(ByteArray::class.java)
  register(Array::class.java)
  register(ArrayList::class.java)
  register(LinkedList::class.java)
  // the way to register java.util.Arrays.ArrayList.ArrayList
  register(listOf("array", "list")::class.java)

  register(HashSet::class.java)
  register(EnumSet::class.java)
  register(TreeSet::class.java)

  register(HashMap::class.java)
  register(TreeMap::class.java)
  register(EnumMap::class.java)
  register(IdentityHashMap::class.java)
  register(LinkedHashMap::class.java)

  register(Collections.EMPTY_LIST.javaClass, DefaultSerializers.CollectionsEmptyListSerializer())
  register(Collections.EMPTY_MAP.javaClass, DefaultSerializers.CollectionsEmptyMapSerializer())
  register(Collections.EMPTY_SET.javaClass, DefaultSerializers.CollectionsEmptySetSerializer())
  register(listOf(null).javaClass, DefaultSerializers.CollectionsSingletonListSerializer())
  register(Collections.singletonMap<Any, Any>(null, null).javaClass, DefaultSerializers.CollectionsSingletonMapSerializer())
  register(setOf(null).javaClass, DefaultSerializers.CollectionsSingletonSetSerializer())
}

@Suppress("IO_FILE_USAGE")
internal object KryoFileSerializer : Serializer<File>(true) {

  override fun write(kryo: Kryo, output: Output, obj: File?) {
    kryo.writeObjectOrNull(output, obj?.path, String::class.java)
  }

  override fun read(kryo: Kryo, input: Input, type: Class<out File?>): File? {
    val path = kryo.readObjectOrNull(input, String::class.java)
    if (path == null) {
      return null
    }
    return File(path)
  }
}