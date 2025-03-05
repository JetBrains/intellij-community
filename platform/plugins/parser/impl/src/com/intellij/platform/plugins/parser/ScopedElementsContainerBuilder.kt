// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser

import com.intellij.platform.plugins.parser.elements.ComponentElement
import com.intellij.platform.plugins.parser.elements.ExtensionPointElement
import com.intellij.platform.plugins.parser.elements.ListenerElement
import com.intellij.platform.plugins.parser.elements.ServiceElement

interface ScopedElementsContainerBuilder {
  fun addService(serviceElement: ServiceElement)
  fun addComponent(componentElement: ComponentElement)
  fun addListener(listenerElement: ListenerElement)
  fun addExtensionPoint(extensionPointElement: ExtensionPointElement)
  fun addExtensionPoints(points: List<ExtensionPointElement>)
  fun removeAllExtensionPoints(): MutableList<ExtensionPointElement>

  fun build(): ScopedElementsContainer
}