// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal val LOG = Logger.getInstance("#com.intellij.configurationStore")

fun <T> deserializeState(stateElement: Element?, stateClass: Class<T>, mergeInto: T?): T? {
  @Suppress("DEPRECATION", "UNCHECKED_CAST")
  return when {
    stateElement == null -> mergeInto
    stateClass == Element::class.java -> stateElement as T?
    com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
      if (mergeInto != null) {
        LOG.error("State is ${stateClass.name}, merge into is $mergeInto, state element text is ${JDOMUtil.writeElement(stateElement)}")
      }

      val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
        .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
        .invoke() as com.intellij.openapi.util.JDOMExternalizable
      t.readExternal(stateElement)
      t as T
    }
    mergeInto == null -> jdomSerializer.deserialize(stateElement, stateClass)
    else -> {
      jdomSerializer.deserializeInto(mergeInto, stateElement)
      mergeInto
    }
  }
}