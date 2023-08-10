// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path

internal fun <T, K> sortByOrderEntity(orderOfItems: List<K>?, elementsByKey: MutableMap<K, MutableList<T>>, sort: List<T>.() -> List<T> = { this }): ArrayList<T> {
  val result = ArrayList<T>()
  orderOfItems?.forEach { name ->
    val elementList = elementsByKey[name] ?: ArrayList()
    elementList.removeFirstOrNull()?.run { result.add(this) }
  }
  result.addAll(elementsByKey.values.flatten().sort())
  return result
}

//todo reuse toPath from VirtualFileUrlManagerUtil.kt
fun VirtualFileUrl.toPath(): Path = Path.of(JpsPathUtil.urlToPath(url))
