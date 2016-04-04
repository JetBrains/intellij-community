/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jsonProtocol

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.io.JsonReaderEx

abstract class EventType<T, R : ResponseResultReader>(val methodName: String) {
  abstract fun read(protocolReader: R, reader: JsonReaderEx): T
}

class EventMap<R : ResponseResultReader>(private val protocolReader: R) {
  private val nameToHandler = ContainerUtil.newConcurrentMap<String, MutableList<(Any?) -> Unit>>()
  private val nameToType = ContainerUtil.newConcurrentMap<String, EventType<*, R>>()

  fun <T : Any?> add(type: EventType<T, R>, handler: (T) -> Unit) {
    nameToType.put(type.methodName, type)
    @Suppress("UNCHECKED_CAST")
    nameToHandler.getOrPut(type.methodName, { ContainerUtil.createLockFreeCopyOnWriteList() }).add(handler as (Any?) -> Unit)
  }

  fun <T> addMulti(vararg types: EventType<out T, R>, eventHandler: (T) -> Unit) {
    for (type in types) {
      add(type, eventHandler)
    }
  }

  fun handleEvent(method: String, data: JsonReaderEx?) {
    val handlers = nameToHandler.get(method)
    if (handlers == null || handlers.isEmpty()) {
      return
    }

    val eventData = data?.let { nameToType[method]!!.read(protocolReader, it) }
    for (handler in handlers) {
      handler(eventData)
    }
  }

  fun <T : Any?> handleEvent(type: EventType<T, R>, event: T) {
    val handlers = nameToHandler.get(type.methodName) ?: return
    for (handler in handlers) {
      handler(event)
    }
  }
}