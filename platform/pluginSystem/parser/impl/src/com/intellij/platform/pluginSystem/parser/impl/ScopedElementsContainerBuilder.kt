// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.parser.impl

import com.intellij.platform.pluginSystem.parser.impl.elements.ComponentElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ExtensionPointElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ListenerElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ServiceElement

interface ScopedElementsContainerBuilder {
  fun addService(serviceElement: ServiceElement)
  fun addComponent(componentElement: ComponentElement)
  fun addListener(listenerElement: ListenerElement)
  fun addExtensionPoint(extensionPointElement: ExtensionPointElement)
  fun addExtensionPoints(points: List<ExtensionPointElement>)
  fun removeAllExtensionPoints(): MutableList<ExtensionPointElement>

  fun build(): ScopedElementsContainer
}