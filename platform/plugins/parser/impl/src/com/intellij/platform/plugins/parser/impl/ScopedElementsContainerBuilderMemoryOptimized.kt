// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.ComponentElement
import com.intellij.platform.plugins.parser.impl.elements.ExtensionPointElement
import com.intellij.platform.plugins.parser.impl.elements.ListenerElement
import com.intellij.platform.plugins.parser.impl.elements.ServiceElement
import com.intellij.util.containers.Java11Shim

internal class ScopedElementsContainerBuilderMemoryOptimized : ScopedElementsContainerBuilder {
  private var _services: MutableList<ServiceElement>? = null
  private var _listeners: MutableList<ListenerElement>? = null
  private var _extensionPoints: MutableList<ExtensionPointElement>? = null
  private var _components: MutableList<ComponentElement>? = null

  override fun addService(serviceElement: ServiceElement) {
    if (_services == null) {
      _services = ArrayList()
    }
    _services!!.add(serviceElement)
  }

  override fun addComponent(componentElement: ComponentElement) {
    if (_components == null) {
      _components = ArrayList()
    }
    _components!!.add(componentElement)
  }

  override fun addListener(listenerElement: ListenerElement) {
    if (_listeners == null) {
      _listeners = ArrayList()
    }
    _listeners!!.add(listenerElement)
  }

  override fun addExtensionPoint(extensionPointElement: ExtensionPointElement) {
    if (_extensionPoints == null) {
      _extensionPoints = ArrayList()
    }
    _extensionPoints!!.add(extensionPointElement)
  }

  override fun addExtensionPoints(points: List<ExtensionPointElement>) {
    if (_extensionPoints == null) {
      _extensionPoints = ArrayList(points)
    } else {
      _extensionPoints!!.addAll(points)
    }
  }

  override fun removeAllExtensionPoints(): MutableList<ExtensionPointElement> {
    val result = _extensionPoints ?: ArrayList()
    _extensionPoints = null
    return result
  }

  override fun build(): ScopedElementsContainer {
    val container = ScopedElementsContainerImpl(
      _services ?: Java11Shim.INSTANCE.listOf(),
      _components ?: Java11Shim.INSTANCE.listOf(),
      _listeners ?: Java11Shim.INSTANCE.listOf(),
      _extensionPoints ?: Java11Shim.INSTANCE.listOf(),
    )
    _services = null
    _components = null
    _listeners = null
    _extensionPoints = null
    return container
  }
}