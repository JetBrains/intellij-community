// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom

import com.intellij.util.xml.NamedEnum

enum class ContentModuleVisibility : NamedEnum {
  PRIVATE, INTERNAL, PUBLIC;

  override fun getValue(): String? = name.lowercase()
}