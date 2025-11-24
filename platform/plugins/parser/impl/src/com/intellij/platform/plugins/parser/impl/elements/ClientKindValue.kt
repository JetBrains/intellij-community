// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.platform.plugins.parser.impl.PluginXmlConst

enum class ClientKindValue(val xmlValue: String) {
  LOCAL(PluginXmlConst.SERVICE_EP_CLIENT_LOCAL_VALUE),
  FRONTEND(PluginXmlConst.SERVICE_EP_CLIENT_FRONTEND_VALUE),
  CONTROLLER(PluginXmlConst.SERVICE_EP_CLIENT_CONTROLLER_VALUE),
  GUEST(PluginXmlConst.SERVICE_EP_CLIENT_GUEST_VALUE),
  OWNER(PluginXmlConst.SERVICE_EP_CLIENT_OWNER_VALUE),
  REMOTE(PluginXmlConst.SERVICE_EP_CLIENT_REMOTE_VALUE),
  ALL(PluginXmlConst.SERVICE_EP_CLIENT_ALL_VALUE);
}