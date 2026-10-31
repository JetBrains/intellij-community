// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.help.impl.HelpManagerImpl
import com.jetbrains.builtInHelp.mapping.HelpMap
import com.jetbrains.builtInHelp.mapping.HelpMapId
import com.jetbrains.builtInHelp.settings.SettingsPage
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import org.jetbrains.io.addNoCache
import org.jetbrains.io.send
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
import javax.xml.bind.JAXBContext

class HelpContentRequestHandler : HelpRequestHandlerBase() {
  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    HelpRequestExecutor.getInstance().execute(request, context) { retainedRequest ->
      serve(urlDecoder, retainedRequest, context)
    }
    return true
  }

  internal suspend fun serve(
    decoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
    isAvailable: suspend (String) -> Boolean = { HelpRequestExecutor.getInstance().availability.isAvailable(it) },
  ) {
    val locale = getRequestLocalePath(request)
    val helpId = decoder.parameters().entries.firstOrNull { (name, values) ->
      name != "keymap" && values.all { it.isEmpty() }
    }?.key
    if (helpId != null || decoder.path().endsWith("/help/")) {
      val topic = helpId ?: "top"
      val keymap = decoder.parameters()["keymap"]?.firstOrNull()
      val onlineUrl = if (Utils.getStoredValue(SettingsPage.OPEN_HELP_FROM_WEB, "true").toBoolean()) {
        HelpUrls.onlineUrl(topic, keymap, Utils.getStoredValue(SettingsPage.OPEN_HELP_BASE_URL, ""),
                           HelpManagerImpl.getDefaultHelpUrl(topic))
      }
      else null
      val destination = if (onlineUrl != null && isAvailable(onlineUrl)) {
        onlineUrl
      }
      else {
        val map = loadMap(locale)
        if (map == null) {
          HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
          return
        }
        val port = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
        HelpUrls.withKeymap("http://127.0.0.1:$port${HelpUrls.localePath(locale)}/help/${map.getUrlForId(topic)}", keymap)
      }
      redirect(destination, request, context)
      return
    }

    val resourceName = decoder.path().substringAfterLast('/')
    if (resourceName == "config.json") {
      val content = Utils.getResourceWithFallback("topics", resourceName, locale)
      if (content == null) {
        HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
        return
      }
      val port = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
      sendData(rewriteConfig(content, "http://127.0.0.1:$port${HelpUrls.localePath(locale)}"), resourceName,
               request, context.channel(), DefaultHttpHeaders())
    }
    else if (!sendResource(resourceName, decoder.path().substringBeforeLast('/'), request, context.channel(), DefaultHttpHeaders())) {
      HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
    }
  }

  internal fun loadMap(locale: String): HelpMap? {
    val bytes = Utils.getResourceWithFallback("topics", "Map.jhm", locale) ?: return null
    return ByteArrayInputStream(bytes).use {
      JAXBContext.newInstance(HelpMap::class.java, HelpMapId::class.java).createUnmarshaller().unmarshal(it) as HelpMap
    }
  }

  internal fun rewriteConfig(content: ByteArray, serverUrl: String): ByteArray {
    val mapper = jacksonObjectMapper()
    val config = mapper.readTree(content) as tools.jackson.databind.node.ObjectNode
    for (name in listOf("searchAlgoliaApiKey", "searchAlgoliaId", "searchAlgoliaIndexName", "versionsService")) {
      config.remove(name)
    }
    for ((name, value) in config.properties().toList()) {
      if (value.isString) {
        config.put(name, value.asString().replace("https://data.services.jetbrains.com", serverUrl))
      }
    }
    return mapper.writeValueAsBytes(config)
  }

  private fun redirect(url: String, request: FullHttpRequest, context: ChannelHandlerContext) {
    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT)
    response.headers().set(HttpHeaderNames.LOCATION, url)
    response.addNoCache()
    response.send(context.channel(), request)
  }
}
