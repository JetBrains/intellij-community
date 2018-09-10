// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.application.PathMacroFilter
import org.jdom.Attribute
import org.jdom.Element

class CompositePathMacroFilter(private val filters: Array<PathMacroFilter>) : PathMacroFilter() {
  override fun skipPathMacros(element: Element) = filters.any { it.skipPathMacros(element) }

  override fun skipPathMacros(attribute: Attribute) = filters.any { it.skipPathMacros(attribute) }

  override fun recursePathMacros(attribute: Attribute) = filters.any { it.recursePathMacros(attribute) }
}
