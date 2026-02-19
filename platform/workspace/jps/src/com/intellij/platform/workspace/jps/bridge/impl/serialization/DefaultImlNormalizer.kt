// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object DefaultImlNormalizer {
  fun normalize(element: Element?) {
    if (element == null) return
    element.addContent(createDeprecatedModuleOptionManager(element))
    element.attributes.removeIf { attribute -> attribute.name != "version" }
  }

  fun createDeprecatedModuleOptionManager(element: Element?): Element {
    //this duplicates logic from ModuleStateStorageManager.beforeElementLoaded which is used to convert attributes to data in an artificial component
    val optionElement = Element("component").setAttribute("name", "DeprecatedModuleOptionManager")
    if (element != null) {
      val iterator = element.attributes.iterator()
      for (attribute in iterator) {
        if (attribute.name != "version") {
          optionElement.addContent(Element("option").setAttribute("key", attribute.name).setAttribute("value", attribute.value))
        }
      }
    }

    return optionElement
  }
}