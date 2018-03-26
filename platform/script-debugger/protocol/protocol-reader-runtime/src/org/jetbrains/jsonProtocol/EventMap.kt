// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jsonProtocol

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.io.JsonReaderEx

abstract class EventType<out T, in R : ResponseResultReader>(val methodName: String) {
  abstract fun read(protocolReader: R, reader: JsonReaderEx): T
}

class EventMap<R : ResponseResultReader>(private val protocolReader: R) {
  private val nameToHandler = ContainerUtil.newConcurrentMap<String, MutableList<(Any?) -> Unit>>()
  private val nameToType = ContainerUtil.newConcurrentMap<String, EventType<*, R>>()

  fun <T : Any?> add(type: EventType<T, R>, handler: (T) -> Unit) {
    nameToType[type.methodName] = type
    @Suppress("UNCHECKED_CAST")
    nameToHandler.getOrPut(type.methodName, { ContainerUtil.createLockFreeCopyOnWriteList() }).add(handler as (Any?) -> Unit)
  }

  fun <T : Any?> remove(type: EventType<T, R>, handler: (T) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    nameToHandler[type.methodName]?.remove(handler as (Any?) -> Unit)
  }

  fun <T> addMulti(vararg types: EventType<T, R>, eventHandler: (T) -> Unit) {
    for (type in types) {
      add(type, eventHandler)
    }
  }

  fun handleEvent(method: String, data: JsonReaderEx?) {
    val handlers = nameToHandler[method]
    if (handlers == null || handlers.isEmpty()) {
      return
    }

    val eventData = data?.let { nameToType[method]!!.read(protocolReader, it) }
    for (handler in handlers) {
      handler(eventData)
    }
  }
}