// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Checks the mapping from the WinHTTP settings to a selector with stubbed readers, so it runs on every OS. */
@Timeout(30)
internal class WindowsProxySearchStrategyTest {
  private val proxied = URI.create("https://proxied.invalid")
  private val direct = URI.create("https://direct.invalid")
  private val noSettings = WinHttp.UserProxyConfig(autoDetect = false, autoConfigUrl = null, proxy = null, proxyBypass = null)

  @Test
  fun `the machine-wide WinHTTP proxy wins over the user settings`() {
    val strategy = strategy(
      defaultConfig = WinHttp.DefaultProxyConfig(accessType = 3, proxy = "machine.invalid:8080", proxyBypass = null),
      userConfig = WinHttp.UserProxyConfig(autoDetect = false, autoConfigUrl = null, proxy = "user.invalid:8080", proxyBypass = null),
    )
    assertThat(strategy.proxySelector!!.select(proxied)).containsExactly(httpProxy("machine.invalid", 8080))
  }

  @Test
  fun `a direct WinHTTP access type falls through to the user settings`() {
    val strategy = strategy(
      defaultConfig = WinHttp.DefaultProxyConfig(accessType = WinHttp.ACCESS_TYPE_NO_PROXY, proxy = "machine.invalid:8080", proxyBypass = null),
      userConfig = WinHttp.UserProxyConfig(autoDetect = false, autoConfigUrl = null, proxy = "user.invalid:8080", proxyBypass = null),
    )
    assertThat(strategy.proxySelector!!.select(proxied)).containsExactly(httpProxy("user.invalid", 8080))
  }

  @Test
  fun `a detected PAC URL wins over the configured one`(@TempDir directory: Path) {
    val detected = pacFile(directory, "detected.pac", "detected.invalid")
    val configured = pacFile(directory, "configured.pac", "configured.invalid")
    val strategy = strategy(
      userConfig = WinHttp.UserProxyConfig(autoDetect = true, autoConfigUrl = configured.toUri().toString(), proxy = null, proxyBypass = null),
      detectedUrl = detected.toUri().toString(),
    )
    assertThat(strategy.proxySelector!!.select(proxied)).containsExactly(httpProxy("detected.invalid", 8181), Proxy.NO_PROXY)
  }

  @Test
  fun `the configured PAC URL is used when detection finds nothing`(@TempDir directory: Path) {
    val configured = pacFile(directory, "configured.pac", "configured.invalid")
    val strategy = strategy(
      userConfig = WinHttp.UserProxyConfig(autoDetect = true, autoConfigUrl = configured.toUri().toString(), proxy = "user.invalid:8080", proxyBypass = null),
      detectedUrl = null,
    )
    assertThat(strategy.proxySelector!!.select(proxied)).containsExactly(httpProxy("configured.invalid", 8181), Proxy.NO_PROXY)
  }

  @Test
  fun `a file URL with two slashes gets the third one`(@TempDir directory: Path) {
    val pac = pacFile(directory, "local.pac", "local.invalid")
    val twoSlashUrl = "file://" + pac.toUri().toString().removePrefix("file:///")
    val strategy = strategy(userConfig = WinHttp.UserProxyConfig(autoDetect = false, autoConfigUrl = twoSlashUrl, proxy = null, proxyBypass = null))
    assertThat(strategy.proxySelector!!.select(proxied)).containsExactly(httpProxy("local.invalid", 8181), Proxy.NO_PROXY)
  }

  @Test
  fun `manual settings honor the bypass list`() {
    val strategy = strategy(
      userConfig = WinHttp.UserProxyConfig(autoDetect = false, autoConfigUrl = null, proxy = "user.invalid:3128", proxyBypass = "direct.invalid;*.corp.invalid"),
    )
    val selector = strategy.proxySelector!!
    assertThat(selector.select(proxied)).containsExactly(httpProxy("user.invalid", 3128))
    assertThat(selector.select(direct)).containsExactly(Proxy.NO_PROXY)
    assertThat(selector.select(URI.create("https://host.corp.invalid"))).containsExactly(Proxy.NO_PROXY)
  }

  @Test
  fun `manual settings can name a proxy per protocol`() {
    val strategy = strategy(
      userConfig = WinHttp.UserProxyConfig(autoDetect = false, autoConfigUrl = null, proxy = "http=plain.invalid:80;https=secure.invalid:443", proxyBypass = null),
    )
    val selector = strategy.proxySelector!!
    assertThat(selector.select(URI.create("http://proxied.invalid"))).containsExactly(httpProxy("plain.invalid", 80))
    assertThat(selector.select(proxied)).containsExactly(httpProxy("secure.invalid", 443))
  }

  @Test
  fun `no settings give no selector`() {
    assertThat(strategy(userConfig = noSettings).proxySelector).isNull()
    assertThat(strategy(defaultConfig = null, userConfig = null).proxySelector).isNull()
  }

  private fun strategy(
    defaultConfig: WinHttp.DefaultProxyConfig? = WinHttp.DefaultProxyConfig(accessType = WinHttp.ACCESS_TYPE_NO_PROXY, proxy = null, proxyBypass = null),
    userConfig: WinHttp.UserProxyConfig?,
    detectedUrl: String? = null,
  ): WindowsProxySearchStrategy = WindowsProxySearchStrategy({ defaultConfig }, { userConfig }, { detectedUrl })

  private fun pacFile(directory: Path, name: String, proxyHost: String): Path = Files.writeString(directory.resolve(name), """
    function FindProxyForURL(url, host) {
      return host == "proxied.invalid" ? "PROXY $proxyHost:8181; DIRECT" : "DIRECT";
    }
  """.trimIndent())

  private fun httpProxy(host: String, port: Int): Proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
}
