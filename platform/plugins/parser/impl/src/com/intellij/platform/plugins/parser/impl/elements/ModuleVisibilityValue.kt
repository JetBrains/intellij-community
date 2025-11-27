// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.platform.plugins.parser.impl.PluginXmlConst

enum class ModuleVisibilityValue(val xmlValue: String) {
  PRIVATE(PluginXmlConst.CONTENT_MODULE_VISIBILITY_PRIVATE_VALUE),
  INTERNAL(PluginXmlConst.CONTENT_MODULE_VISIBILITY_INTERNAL_VALUE),
  PUBLIC(PluginXmlConst.CONTENT_MODULE_VISIBILITY_PUBLIC_VALUE),
}