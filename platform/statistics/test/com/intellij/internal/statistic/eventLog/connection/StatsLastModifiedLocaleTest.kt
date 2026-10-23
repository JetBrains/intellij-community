// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection

import com.intellij.internal.statistic.eventLog.connection.metadata.StatsBasicConnectionSettings
import com.intellij.internal.statistic.eventLog.connection.metadata.createJvmHttpClient
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpResponse
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Locale

class StatsLastModifiedLocaleTest {
  private val header = "Tue, 06 Oct 2026 10:15:00 GMT"
  private val expected = Instant.parse("2026-10-06T10:15:00Z").toEpochMilli()

  private lateinit var defaultLocale: Locale
  private lateinit var formatLocale: Locale

  @BeforeEach
  fun setUp() {
    defaultLocale = Locale.getDefault()
    formatLocale = Locale.getDefault(Locale.Category.FORMAT)
    // An English display language with a German region, as on English Windows in Germany.
    Locale.setDefault(Locale.ENGLISH)
    Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
  }

  @AfterEach
  fun tearDown() {
    Locale.setDefault(defaultLocale)
    Locale.setDefault(Locale.Category.FORMAT, formatLocale)
  }

  @Test
  fun `http dates parse under a non-English format locale`() {
    assertEquals(expected, StatsHttpResponse.parseDate(header))
    assertEquals(expected, StatsHttpResponse.parseDate("Tue, 06-Oct-26 10:15:00 GMT"))
  }

  @Test
  fun `the FUS http client reads Last-Modified under a non-English format locale`() {
    val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    server.createContext("/events-scheme.json") { exchange ->
      exchange.responseHeaders.add("Last-Modified", header)
      exchange.sendResponseHeaders(200, -1)
      exchange.close()
    }
    server.start()
    try {
      val client = StatsBasicConnectionSettings("test").createJvmHttpClient()
      assertEquals(expected, client.lastModified("http://127.0.0.1:${server.address.port}/events-scheme.json"))
    }
    finally {
      server.stop(0)
    }
  }
}
