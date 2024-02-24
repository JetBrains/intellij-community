// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.serialization.MutableAccessor
import com.intellij.util.ArrayUtil
import com.intellij.util.xml.dom.XmlElement
import org.jdom.Element
import java.util.*

internal class ArrayBinding(valueClass: Class<*>, accessor: MutableAccessor?, serializer: Serializer) : AbstractCollectionBinding(
  valueClass.componentType, accessor, serializer) {
  override fun getCollectionTagName(target: Any?): String {
    return "array"
  }

  override fun doDeserializeJdomList(context: Any?, elements: List<Element?>): Any {
    val size = elements.size
    val result: Array<Any> = ArrayUtil.newArray(itemType, size)
    for (i in 0 until size) {
      result[i] = deserializeItem(elements[i]!!, context)
    }
    return result
  }

  override fun doDeserializeList(context: Any?, elements: List<XmlElement>): Any {
    val size = elements.size
    val result = java.lang.reflect.Array.newInstance(itemType, size) as Array<Any>
    for (i in 0 until size) {
      result[i] = deserializeItem(elements[i], context)
    }
    return result
  }

  public override fun getCollection(bean: Any): Collection<Any?> {
    val list = bean as Array<Any>
    return if (list.size == 0) emptyList<Any>() else Arrays.asList(*list)
  }
}
