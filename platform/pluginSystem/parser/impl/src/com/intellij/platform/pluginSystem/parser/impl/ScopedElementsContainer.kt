// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.parser.impl

import com.intellij.platform.pluginSystem.parser.impl.elements.ComponentElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ExtensionPointElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ListenerElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ServiceElement

interface ScopedElementsContainer {
  val services: List<ServiceElement>
  val components: List<ComponentElement>
  val listeners: List<ListenerElement>
  val extensionPoints: List<ExtensionPointElement>
}