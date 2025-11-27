// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.platform.plugins.parser.impl.PluginXmlConst

enum class OSValue(val xmlValue: String) {
  MAC(PluginXmlConst.OS_MAC_VALUE),
  LINUX(PluginXmlConst.OS_LINUX_VALUE),
  WINDOWS(PluginXmlConst.OS_WINDOWS_VALUE),
  UNIX(PluginXmlConst.OS_UNIX_VALUE),
  FREEBSD(PluginXmlConst.OS_FREEBSD_VALUE);
}
