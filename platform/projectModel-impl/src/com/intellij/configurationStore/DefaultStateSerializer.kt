// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.util.xmlb.JdomAdapter
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

@Internal
fun <T : Any> deserializeState(stateElement: Element?, stateClass: Class<T>): T? {
  @Suppress("DEPRECATION", "UNCHECKED_CAST")
  return when {
    stateElement == null -> null
    stateClass === Element::class.java -> stateElement as T?
    com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
      deserializeJdomExternalizable(stateClass, stateElement)
    }
    else -> jdomSerializer.deserialize(stateElement, stateClass, JdomAdapter)
  }
}

@Internal
@Suppress("DEPRECATION", "UNCHECKED_CAST")
fun <T : Any> deserializeJdomExternalizable(stateClass: Class<T>, stateElement: Element?): T {
  val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
    .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
    .invoke() as com.intellij.openapi.util.JDOMExternalizable
  t.readExternal(stateElement)
  return t as T
}
