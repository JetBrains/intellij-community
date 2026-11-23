// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.parser.impl.elements

/**
 * A `<technology id="..." name="..." description="..." icon="..."/>` element of a plugin or content module descriptor.
 * The parser keeps each attribute as written, and `null` for a missing or empty attribute. The reader of the element
 * checks the values.
 */
class TechnologyElement(
  @JvmField val id: String?,
  @JvmField val name: String?,
  @JvmField val description: String?,
  @JvmField val icon: String?,
) {
  override fun toString(): String {
    return buildString {
      append("TechnologyElement(id=$id")
      if (name != null) append(", name=$name")
      if (description != null) append(", description=$description")
      if (icon != null) append(", icon=$icon")
      append(")")
    }
  }
}
