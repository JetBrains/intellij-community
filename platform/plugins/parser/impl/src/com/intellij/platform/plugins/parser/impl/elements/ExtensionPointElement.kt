// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

class ExtensionPointElement(
  @JvmField val name: String?,
  @JvmField val qualifiedName: String?,
  @JvmField val `interface`: String?,
  @JvmField val beanClass: String?,
  @JvmField val hasAttributes: Boolean,
  @JvmField val isDynamic: Boolean,
) {
  init {
    require(name != null || qualifiedName != null) { "neither `name` nor `qualifiedName` specified" }
    require((`interface` != null) != (beanClass != null)) { "only one of `interface` or `beanClass` must be specified" }
  }
}