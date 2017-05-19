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
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

private val LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.DefaultStateSerializer")

fun <T> deserializeState(stateElement: Element?, stateClass: Class<T>, mergeInto: T?): T? {
  @Suppress("DEPRECATION")
  if (stateElement == null) {
    return mergeInto
  }
  else if (stateClass == Element::class.java) {
    @Suppress("UNCHECKED_CAST")
    return stateElement as T?
  }
  else if (JDOMExternalizable::class.java.isAssignableFrom(stateClass)) {
    if (mergeInto != null) {
      val elementText = JDOMUtil.writeElement(stateElement)
      LOG.error("State is ${stateClass.name}, merge into is ${mergeInto.toString()}, state element text is $elementText")
    }

    val t = ReflectionUtil.newInstance(stateClass)
    (t as JDOMExternalizable).readExternal(stateElement)
    return t
  }
  else if (mergeInto == null) {
    return XmlSerializer.deserialize(stateElement, stateClass)
  }
  else {
    XmlSerializer.deserializeInto(mergeInto, stateElement)
    return mergeInto
  }
}