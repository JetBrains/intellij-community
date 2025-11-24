// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.platform.plugins.parser.impl.PluginXmlConst

enum class PreloadModeValue(val xmlValue: String) {
  TRUE(PluginXmlConst.SERVICE_EP_PRELOAD_TRUE_VALUE),
  FALSE(PluginXmlConst.SERVICE_EP_PRELOAD_FALSE_VALUE),
  AWAIT(PluginXmlConst.SERVICE_EP_PRELOAD_AWAIT_VALUE),
  NOT_HEADLESS(PluginXmlConst.SERVICE_EP_PRELOAD_NOT_HEADLESS_VALUE),
  NOT_LIGHT_EDIT(PluginXmlConst.SERVICE_EP_PRELOAD_NOT_LIGHT_EDIT_VALUE);
}