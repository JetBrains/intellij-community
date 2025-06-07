// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.ComponentElement
import com.intellij.platform.plugins.parser.impl.elements.ExtensionPointElement
import com.intellij.platform.plugins.parser.impl.elements.ListenerElement
import com.intellij.platform.plugins.parser.impl.elements.ServiceElement

interface ScopedElementsContainer {
  val services: List<ServiceElement>
  val components: List<ComponentElement>
  val listeners: List<ListenerElement>
  val extensionPoints: List<ExtensionPointElement>
}