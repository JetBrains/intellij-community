// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.builtInHelp.settings.SettingsPage
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.stream.ChunkedWriteHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class HelpHttpTest {
  @Test
  fun `offline entry redirects temporarily and preserves the fragment`(): Unit = timeoutRunBlocking {
    withOfflineResponse("/help/?editor.settings&keymap=Mac+OS+X") { response ->
      assertEquals(HttpResponseStatus.TEMPORARY_REDIRECT, response.status())
      assertTrue(response.headers()[HttpHeaderNames.LOCATION].endsWith("/help/editor.html?keymap=Mac+OS+X#shortcuts"))
      assertTrue(response.headers()[HttpHeaderNames.CACHE_CONTROL].contains("no-store"))
    }
  }

  @Test
  fun `dark images fall back to the bundled image`(): Unit = timeoutRunBlocking {
    withOfflineResponse("/zh-cn/help/img/example_dark.svg") { response ->
      assertEquals(HttpResponseStatus.OK, response.status())
      assertFalse(response.headers().contains("X-Test-Request"))
      assertNull(response.headers()[HttpHeaderNames.HOST])
    }
  }

  @Test
  fun `missing resources return 404`(): Unit = timeoutRunBlocking {
    withOfflineResponse("/help/missing.html") { response ->
      assertEquals(HttpResponseStatus.NOT_FOUND, response.status())
    }
  }

  @Test
  fun `online help falls back when the configured destination is unavailable`(): Unit = timeoutRunBlocking {
    for (online in listOf(true, false)) {
      withResponse("/help/?editor.settings&keymap=Windows", preferOnline = true, isAvailable = { url ->
        assertEquals("https://docs.example.com/idea/263/?editor.settings&keymap=Windows", url)
        online
      }) { response ->
        val destination = response.headers()[HttpHeaderNames.LOCATION]
        if (online) assertTrue(destination.startsWith("https://docs.example.com/idea/263/"))
        else assertTrue(destination.endsWith("/help/editor.html?keymap=Windows#shortcuts"))
      }
    }
  }

  private suspend fun withOfflineResponse(uri: String, assertion: (HttpResponse) -> Unit) {
    withResponse(uri, false, { error("Offline help must not probe the network") }, assertion)
  }

  private suspend fun withResponse(
    uri: String,
    preferOnline: Boolean,
    isAvailable: suspend (String) -> Boolean,
    assertion: (HttpResponse) -> Unit,
  ) {
    val previous = Utils.getStoredValue(SettingsPage.OPEN_HELP_FROM_WEB, "true")
    val previousBase = Utils.getStoredValue(SettingsPage.OPEN_HELP_BASE_URL, "")
    Utils.setStoredValue(SettingsPage.OPEN_HELP_FROM_WEB, preferOnline.toString())
    Utils.setStoredValue(SettingsPage.OPEN_HELP_BASE_URL, "https://docs.example.com/idea/263/")
    val channel = EmbeddedChannel(ChunkedWriteHandler(), ChannelInboundHandlerAdapter())
    val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
    request.headers().set(HttpHeaderNames.HOST, "127.0.0.1")
    request.headers().set("X-Test-Request", "must-not-be-reflected")
    try {
      HelpContentRequestHandler().serve(QueryStringDecoder(uri), request, channel.pipeline().lastContext(), isAvailable)
      assertion(channel.readOutbound())
    }
    finally {
      request.release()
      channel.finishAndReleaseAll()
      Utils.setStoredValue(SettingsPage.OPEN_HELP_FROM_WEB, previous)
      Utils.setStoredValue(SettingsPage.OPEN_HELP_BASE_URL, previousBase)
    }
  }
}
