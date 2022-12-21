// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

internal fun <T> sortByOrderEntity(orderOfItems: List<String>?, elementsByKey: HashMap<String, MutableList<T>>): ArrayList<T> {
  val result = ArrayList<T>()
  orderOfItems?.forEach { name ->
    val elements = elementsByKey.remove(name) ?: emptyList()
    result.addAll(elements)
  }
  elementsByKey.values.forEach {
    result.addAll(it)
  }
  return result
}
