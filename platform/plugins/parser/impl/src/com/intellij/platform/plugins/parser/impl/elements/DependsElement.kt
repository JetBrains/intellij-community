// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

class DependsElement(
  @JvmField val pluginId: String,
  @JvmField val isOptional: Boolean,
  @JvmField val configFile: String?,
)