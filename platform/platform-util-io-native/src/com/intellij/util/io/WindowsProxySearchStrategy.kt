// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.github.markusbernhardt.proxy.search.desktop.win.CommonWindowsSearchStrategy
import com.github.markusbernhardt.proxy.util.ProxyUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.net.ProxySelector

private val LOG = logger<WindowsProxySearchStrategy>()

/**
 * The Windows proxy search: first the machine-wide WinHTTP settings, then the Internet Options of the current user.
 *
 * The user settings give a PAC selector when auto-detection or an auto-config URL is set, and a fixed selector otherwise.
 * The class reads the settings with [WinHttp] and builds the selectors with the proxy-vole classes that need no JNA.
 */
@ApiStatus.Internal
class WindowsProxySearchStrategy internal constructor(
  private val defaultProxyConfig: () -> WinHttp.DefaultProxyConfig?,
  private val userProxyConfig: () -> WinHttp.UserProxyConfig?,
  private val detectAutoProxyConfigUrl: () -> String?,
) : CommonWindowsSearchStrategy() {
  constructor() : this(WinHttp::defaultProxyConfig, WinHttp::currentUserProxyConfig, WinHttp::detectAutoProxyConfigUrl)

  override fun getName(): String = "Windows"

  override fun getProxySelector(): ProxySelector? {
    val defaultConfig = defaultProxyConfig()
    if (defaultConfig != null && defaultConfig.accessType != WinHttp.ACCESS_TYPE_NO_PROXY && defaultConfig.proxy != null) {
      LOG.debug { "WinHTTP default proxy: ${defaultConfig.proxy}, bypass: ${defaultConfig.proxyBypass}" }
      return fixedSelector(defaultConfig.proxy, defaultConfig.proxyBypass)
    }

    val userConfig = userProxyConfig() ?: return null
    val pacUrl = pacUrl(userConfig)
    if (pacUrl != null) {
      LOG.debug { "PAC URL: $pacUrl" }
      return ProxyUtil.buildPacSelectorForUrl(pacUrl)
    }
    val proxy = userConfig.proxy ?: return null
    LOG.debug { "manual proxy: $proxy, bypass: ${userConfig.proxyBypass}" }
    return fixedSelector(proxy, userConfig.proxyBypass)
  }

  private fun pacUrl(config: WinHttp.UserProxyConfig): String? {
    var url = if (config.autoDetect) detectAutoProxyConfigUrl() else null
    if (url.isNullOrBlank()) {
      url = config.autoConfigUrl
    }
    if (url.isNullOrBlank()) {
      return null
    }
    // Windows stores a local PAC path as `file://C:\...`; the URL class needs the third slash
    return if (url.startsWith("file://") && !url.startsWith("file:///")) "file:///" + url.substring("file://".length) else url
  }

  private fun fixedSelector(proxy: String, bypassList: String?): ProxySelector = setByPassListOnSelector(bypassList, buildProtocolDispatchSelector(parseProxyList(proxy)))
}
