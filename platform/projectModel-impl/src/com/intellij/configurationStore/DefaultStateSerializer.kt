// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ReflectionUtil
import org.jdom.Element

internal val LOG = Logger.getInstance("#com.intellij.configurationStore")

fun <T> deserializeState(stateElement: Element?, stateClass: Class<T>, mergeInto: T?): T? {
  @Suppress("DEPRECATION", "UNCHECKED_CAST")
  return when {
    stateElement == null -> mergeInto
    stateClass == Element::class.java -> stateElement as T?
    JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
      if (mergeInto != null) {
        LOG.error("State is ${stateClass.name}, merge into is $mergeInto, state element text is ${JDOMUtil.writeElement(stateElement)}")
      }

      val t = ReflectionUtil.newInstance(stateClass, false)
      (t as JDOMExternalizable).readExternal(stateElement)
      t
    }
    mergeInto == null -> stateElement.deserialize(stateClass)
    else -> {
      stateElement.deserializeInto(mergeInto)
      mergeInto
    }
  }
}