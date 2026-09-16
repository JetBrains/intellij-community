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

@Timeout(30)
internal class IoServiceTest {
  @Test
  fun `a PAC file selects a proxy without a network request`(@TempDir directory: Path) {
    val pacFile = directory.resolve("proxy.pac")
    Files.writeString(pacFile, """
      function FindProxyForURL(url, host) {
        return host == "proxied.invalid" ? "PROXY proxy.invalid:8181; DIRECT" : "DIRECT";
      }
    """.trimIndent())
    val selector = requireNotNull(IoServiceImpl().getProxySelector(pacFile.toUri().toString()))
    assertThat(selector.select(URI.create("https://proxied.invalid"))).containsExactly(
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.invalid", 8181)),
      Proxy.NO_PROXY,
    )
    assertThat(selector.select(URI.create("https://direct.invalid"))).containsExactly(Proxy.NO_PROXY)
  }
}
