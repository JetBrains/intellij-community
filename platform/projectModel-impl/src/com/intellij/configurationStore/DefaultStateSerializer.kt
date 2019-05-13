/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

      val t = ReflectionUtil.newInstance(stateClass)
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