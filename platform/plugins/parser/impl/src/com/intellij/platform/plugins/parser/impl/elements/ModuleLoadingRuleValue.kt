// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.platform.plugins.parser.impl.PluginXmlConst

enum class ModuleLoadingRuleValue(val xmlValue: String) {
  REQUIRED(PluginXmlConst.CONTENT_MODULE_LOADING_REQUIRED_VALUE),
  EMBEDDED(PluginXmlConst.CONTENT_MODULE_LOADING_EMBEDDED_VALUE),
  OPTIONAL(PluginXmlConst.CONTENT_MODULE_LOADING_OPTIONAL_VALUE),
  ON_DEMAND(PluginXmlConst.CONTENT_MODULE_LOADING_ON_DEMAND_VALUE),
}