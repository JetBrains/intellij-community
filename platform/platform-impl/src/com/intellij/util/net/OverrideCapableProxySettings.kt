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
  /** This class is intended to replace `HttpConfigurable` once the latter becomes ready for retirement. */
  @Suppress("PropertyName")
  internal data class State(
    val PROXY_TYPE: Type,
    val PAC_URL: String,
    val PROXY_PROTOCOL: ProxyProtocol,
    val PROXY_HOST: String,
    val PROXY_PORT: Int,
    val PROXY_EXCEPTIONS: String,
  ) {
    enum class Type { DIRECT, AUTO, PAC, STATIC }

    constructor() : this(Type.DIRECT, "", ProxyProtocol.HTTP, "", 0, "")

    fun asProxyConfiguration(): ProxyConfiguration = when (PROXY_TYPE) {
      Type.DIRECT -> ProxyConfiguration.direct
      Type.AUTO -> ProxyConfiguration.autodetect
      Type.PAC -> {
        try {
          @Suppress("DEPRECATION") val url = URL(PAC_URL)
          ProxyConfiguration.proxyAutoConfiguration(url)
        }
        catch (_: MalformedURLException) {
          ProxyConfiguration.autodetect
        }
      }
      Type.STATIC -> ProxyConfiguration.proxy(PROXY_PROTOCOL, PROXY_HOST, PROXY_PORT, PROXY_EXCEPTIONS)
    }

    fun amend(proxyConfiguration: ProxyConfiguration): State {
      var pacUrl = PAC_URL
      var protocol = PROXY_PROTOCOL
      var host = PROXY_HOST
      var port = PROXY_PORT
      var exceptions = PROXY_EXCEPTIONS
      val type = when (proxyConfiguration) {
        is ProxyConfiguration.DirectProxy -> Type.DIRECT
        is ProxyConfiguration.AutoDetectProxy -> Type.AUTO
        is ProxyConfiguration.ProxyAutoConfiguration -> {
          pacUrl = proxyConfiguration.pacUrl.toExternalForm()
          Type.PAC
        }
        is ProxyConfiguration.StaticProxyConfiguration -> {
          protocol = proxyConfiguration.protocol
          host = proxyConfiguration.host
          port = proxyConfiguration.port
          exceptions = proxyConfiguration.exceptions
          Type.STATIC
        }
      }
      return State(type, pacUrl, protocol, host, port, exceptions)
    }
  }

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

  internal var proxyState: State
    get() = httpConfigurable.getProxyState()
    set(value) = httpConfigurable.setFromProxyState(value)

  override fun getProxyConfiguration(): ProxyConfiguration {
    if (isOverrideEnabled) {
      val overrideConf = overrideProvider?.proxyConfigurationProvider?.getProxyConfiguration()
      if (overrideConf != null) {
        return overrideConf
      }
    }
    return proxyState.asProxyConfiguration()
  }

  override fun setProxyConfiguration(proxyConfiguration: ProxyConfiguration) {
    if (!isOverrideEnabled || overrideProvider == null) {
      httpConfigurable.setFromProxyState(proxyState.amend(proxyConfiguration))
    }
  }

  @Suppress("removal", "DEPRECATION")
  private fun HttpConfigurable.getProxyState(): State {
    val type = when {
      USE_PROXY_PAC && USE_PAC_URL -> State.Type.PAC
      USE_PROXY_PAC -> State.Type.AUTO
      USE_HTTP_PROXY -> State.Type.STATIC
      else -> State.Type.DIRECT
    }
    val pacUrl = PAC_URL.orEmpty()
    val protocol = if (PROXY_TYPE_IS_SOCKS) ProxyProtocol.SOCKS else ProxyProtocol.HTTP
    return State(type, pacUrl, protocol, PROXY_HOST.orEmpty(), PROXY_PORT, PROXY_EXCEPTIONS.orEmpty())
  }

  @Suppress("removal", "DEPRECATION")
  private fun HttpConfigurable.setFromProxyState(state: State) {
    USE_PROXY_PAC = state.PROXY_TYPE == State.Type.AUTO || state.PROXY_TYPE == State.Type.PAC
    USE_PAC_URL = state.PROXY_TYPE == State.Type.PAC
    PAC_URL = state.PAC_URL
    USE_HTTP_PROXY = state.PROXY_TYPE == State.Type.STATIC
    PROXY_TYPE_IS_SOCKS = state.PROXY_PROTOCOL == ProxyProtocol.SOCKS
    PROXY_HOST = state.PROXY_HOST
    PROXY_PORT = state.PROXY_PORT
    PROXY_EXCEPTIONS = state.PROXY_EXCEPTIONS
  }
}
