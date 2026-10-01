// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.junit5.TestApplication
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Checks that [ConfirmingTrustManager] rejects a trusted certificate that names another host.
 * The socket cases use [HttpsURLConnection], and the engine cases use [HttpClient].
 */
@TestApplication
internal class ConfirmingTrustManagerHostNameTest {
  @Test fun acceptsMatchingHostName(@TempDir tempDir: Path) {
    val server = startServer(tempDir, "localhost")
    try {
      val trustManager = trustManagerWithSystemCertificate(tempDir, server.certificate)

      assertThat(connectWithSocket(trustManager, server.url)).isEqualTo(200)
      assertThat(connectWithEngine(trustManager, server.url)).isEqualTo(200)
    }
    finally {
      server.stop()
    }
  }

  @Test fun rejectsWrongHostNameWithSocket(@TempDir tempDir: Path) {
    val server = startServer(tempDir, WRONG_HOST)
    try {
      val trustManager = trustManagerWithSystemCertificate(tempDir, server.certificate)

      assertThatThrownBy { connectWithSocket(trustManager, server.url) }
        .hasRootCauseInstanceOf(CertificateException::class.java)
    }
    finally {
      server.stop()
    }
  }

  @Test fun rejectsWrongHostNameWithEngine(@TempDir tempDir: Path) {
    val server = startServer(tempDir, WRONG_HOST)
    try {
      val trustManager = trustManagerWithSystemCertificate(tempDir, server.certificate)

      assertThatThrownBy { connectWithEngine(trustManager, server.url) }
        .hasRootCauseInstanceOf(CertificateException::class.java)
    }
    finally {
      server.stop()
    }
  }

  /**
   * With automatic acceptance, a certificate that the custom trust store rejects is accepted without a dialog.
   * A host name failure must not take that path.
   */
  @Test fun rejectsWrongHostNameFromCustomStoreWithAutomaticAcceptance(@TempDir tempDir: Path) {
    val server = startServer(tempDir, WRONG_HOST)
    val config = CertificateManager.getInstance().state
    val oldAcceptAutomatically = config.ACCEPT_AUTOMATICALLY
    try {
      config.ACCEPT_AUTOMATICALLY = true
      val trustManager = ConfirmingTrustManager.createForStorage(tempDir.resolve("cacerts").toString(), CertificateManager.DEFAULT_PASSWORD)
      assertThat(trustManager.customManager.addCertificate(server.certificate)).isTrue()

      assertThatThrownBy { connectWithSocket(trustManager, server.url) }
        .hasRootCauseInstanceOf(CertificateException::class.java)
      assertThatThrownBy { connectWithEngine(trustManager, server.url) }
        .hasRootCauseInstanceOf(CertificateException::class.java)
    }
    finally {
      config.ACCEPT_AUTOMATICALLY = oldAcceptAutomatically
      server.stop()
    }
  }
}

private const val WRONG_HOST = "wrong.host.badssl.com"
private const val PASSWORD = "changeit"

private class TestServer(private val server: HttpsServer, val certificate: X509Certificate) {
  val url: String = "https://localhost:${server.address.port}/"

  fun stop() {
    server.stop(0)
  }
}

/** Starts an HTTPS server on `localhost` with a self-signed certificate for [certificateHost]. */
private fun startServer(tempDir: Path, certificateHost: String): TestServer {
  val keyStore = createKeyStore(tempDir.resolve("server.p12"), certificateHost)
  val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
  keyManagerFactory.init(keyStore, PASSWORD.toCharArray())
  val serverContext = SSLContext.getInstance("TLS")
  serverContext.init(keyManagerFactory.keyManagers, null, null)

  val server = HttpsServer.create(InetSocketAddress("localhost", 0), 0)
  server.httpsConfigurator = HttpsConfigurator(serverContext)
  server.createContext("/") { exchange ->
    exchange.sendResponseHeaders(200, -1)
    exchange.close()
  }
  server.start()
  return TestServer(server, keyStore.getCertificate("server") as X509Certificate)
}

private fun createKeyStore(path: Path, certificateHost: String): KeyStore {
  val keytool = Path.of(System.getProperty("java.home"), "bin", if (SystemInfoRt.isWindows) "keytool.exe" else "keytool")
  val process = ProcessBuilder(
    keytool.toString(), "-genkeypair", "-noprompt",
    "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
    "-dname", "CN=$certificateHost", "-ext", "SAN=dns:$certificateHost",
    "-storetype", "PKCS12", "-keystore", path.toString(), "-storepass", PASSWORD, "-keypass", PASSWORD,
  ).redirectErrorStream(true).start()
  val output = process.inputStream.bufferedReader().readText()
  check(process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0) { "keytool failed: $output" }

  val keyStore = KeyStore.getInstance("PKCS12")
  Files.newInputStream(path).use { keyStore.load(it, PASSWORD.toCharArray()) }
  return keyStore
}

/** The system trust managers do not trust the test certificate, so the test adds one that does. */
private fun trustManagerWithSystemCertificate(tempDir: Path, certificate: X509Certificate): ConfirmingTrustManager {
  val trustManager = ConfirmingTrustManager.createForStorage(tempDir.resolve("cacerts").toString(), CertificateManager.DEFAULT_PASSWORD)
  trustManager.addSystemTrustManager(ConfirmingTrustManager.createTrustManagerFromCertificates(listOf(certificate)))
  return trustManager
}

private fun clientContext(trustManager: ConfirmingTrustManager): SSLContext {
  val context = SSLContext.getInstance("TLS")
  context.init(null, arrayOf(trustManager), null)
  return context
}

/** Connects through an `SSLSocket`. The default host name verifier makes the JDK ask the trust manager for the host name check. */
private fun connectWithSocket(trustManager: ConfirmingTrustManager, url: String): Int {
  val connection = URI(url).toURL().openConnection(Proxy.NO_PROXY) as HttpsURLConnection
  connection.sslSocketFactory = clientContext(trustManager).socketFactory
  try {
    return connection.responseCode
  }
  finally {
    connection.disconnect()
  }
}

/** Connects through an `SSLEngine`. [HttpClient] asks the trust manager for the host name check by default. */
private fun connectWithEngine(trustManager: ConfirmingTrustManager, url: String): Int {
  HttpClient.newBuilder()
    .sslContext(clientContext(trustManager))
    .proxy(HttpClient.Builder.NO_PROXY)
    .build()
    .use { client ->
      return client.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode()
    }
}
