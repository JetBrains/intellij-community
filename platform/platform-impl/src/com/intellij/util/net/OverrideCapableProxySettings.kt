// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.Ref
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
import java.net.MalformedURLException
import java.net.URL

private const val OVERRIDE_ENABLED_PROPERTY = "intellij.platform.proxy.override.enabled"
private val EP_NAME: ExtensionPointName<ProxySettingsOverrideProvider> = ExtensionPointName("com.intellij.proxySettingsOverrideProvider")

internal class OverrideCapableProxySettings : ProxySettings, ProxyConfigurationProvider {
  @Suppress("removal", "DEPRECATION")
  private val httpConfigurable get() = HttpConfigurable.getInstance()

  internal var isOverrideEnabled: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(OVERRIDE_ENABLED_PROPERTY, true)
    set(value) = PropertiesComponent.getInstance().setValue(OVERRIDE_ENABLED_PROPERTY, value, true)

  internal val overrideProvider: ProxySettingsOverrideProvider?
    get() = EP_NAME.computeIfAbsent(OverrideCapableProxySettings::class.java) {
      Ref(EP_NAME.lazySequence().firstOrNull { it.shouldUserSettingsBeOverriden })
    }.get()

  internal fun getProviderPluginName(overrideProvider: ProxySettingsOverrideProvider): String? {
    var result: PluginDescriptor? = null
    EP_NAME.processWithPluginDescriptor { provider, plugin ->
      if (overrideProvider === provider) {
        result = plugin
      }
    }
    return result?.name
  }

  internal val originalProxyConfiguration: ProxyConfiguration get() = httpConfigurable.getProxyConfiguration()

  override fun getProxyConfiguration(): ProxyConfiguration {
    if (isOverrideEnabled) {
      val overrideConf = overrideProvider?.proxyConfigurationProvider?.getProxyConfiguration()
      if (overrideConf != null) {
        return overrideConf
      }
    }
    return originalProxyConfiguration
  }

  override fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration) {
    if (!isOverrideEnabled || overrideProvider == null) {
      httpConfigurable.setFromProxyConfiguration(proxyConfiguration)
    }
  }

  @Suppress("removal", "DEPRECATION")
  private fun HttpConfigurable.getProxyConfiguration(): ProxyConfiguration {
    try {
      return when {
        USE_PROXY_PAC -> {
          val pacUrl = PAC_URL
          if (USE_PAC_URL && !pacUrl.isNullOrEmpty()) {
            try {
              ProxyConfiguration.proxyAutoConfiguration(URL(pacUrl))
            } catch (_: MalformedURLException) {
              ProxyConfiguration.autodetect
            }
          }
          else {
            ProxyConfiguration.autodetect
          }
        }
        USE_HTTP_PROXY -> {
          val protocol = if (PROXY_TYPE_IS_SOCKS) ProxyProtocol.SOCKS else ProxyProtocol.HTTP
          ProxyConfiguration.proxy(protocol, PROXY_HOST, PROXY_PORT, PROXY_EXCEPTIONS ?: "")
        }
        else -> ProxyConfiguration.direct
      }
    }
    catch (_: IllegalArgumentException) { // just in case
      return ProxySettings.defaultProxyConfiguration
    }
  }

  @Suppress("removal", "DEPRECATION")
  private fun HttpConfigurable.setFromProxyConfiguration(proxyConf: ProxyConfiguration) {
    when (proxyConf) {
      is ProxyConfiguration.DirectProxy -> {
        USE_HTTP_PROXY = false
        USE_PROXY_PAC = false
      }
      is ProxyConfiguration.AutoDetectProxy -> {
        USE_HTTP_PROXY = false
        USE_PROXY_PAC = true
        USE_PAC_URL = false
        PAC_URL = null
      }
      is ProxyConfiguration.ProxyAutoConfiguration -> {
        USE_HTTP_PROXY = false
        USE_PROXY_PAC = true
        USE_PAC_URL = true
        PAC_URL = proxyConf.pacUrl.toString()
      }
      is ProxyConfiguration.StaticProxyConfiguration -> {
        USE_PROXY_PAC = false
        USE_HTTP_PROXY = true
        PROXY_TYPE_IS_SOCKS = proxyConf.protocol == ProxyProtocol.SOCKS
        PROXY_HOST = proxyConf.host
        PROXY_PORT = proxyConf.port
        PROXY_EXCEPTIONS = proxyConf.exceptions
      }
    }
  }
}
