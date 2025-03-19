// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.util.xml.dom.XmlElement

open class ActionElement(
  @JvmField val name: ActionElementName,
  @JvmField val element: XmlElement,
  @JvmField val resourceBundle: String?,
) {
  @Suppress("EnumEntryName")
  enum class ActionElementName {
    action, group, separator, reference, unregister, prohibit,
  }

  class ActionElementMisc(
    name: ActionElementName,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionElement(name, element, resourceBundle)

  class ActionDescriptorAction(
    @JvmField val className: String,
    @JvmField val isInternal: Boolean,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionElement(name = ActionElementName.action, element = element, resourceBundle = resourceBundle)

  class ActionElementGroup(
    @JvmField val className: String?,
    @JvmField val id: String?,
    element: XmlElement,
    resourceBundle: String?,
  ) : ActionElement(name = ActionElementName.group, element = element, resourceBundle = resourceBundle)
}