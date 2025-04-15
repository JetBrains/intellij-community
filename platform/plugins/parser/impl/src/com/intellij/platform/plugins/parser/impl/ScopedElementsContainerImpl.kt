// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.ComponentElement
import com.intellij.platform.plugins.parser.impl.elements.ExtensionPointElement
import com.intellij.platform.plugins.parser.impl.elements.ListenerElement
import com.intellij.platform.plugins.parser.impl.elements.ServiceElement

internal class ScopedElementsContainerImpl(
  override val services: List<ServiceElement>,
  override val components: List<ComponentElement>,
  override val listeners: List<ListenerElement>,
  override val extensionPoints: List<ExtensionPointElement>,
) : ScopedElementsContainer