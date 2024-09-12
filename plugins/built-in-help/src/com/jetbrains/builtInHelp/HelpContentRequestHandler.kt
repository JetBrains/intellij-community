// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ResourceUtil
import com.jetbrains.builtInHelp.mapping.HelpMap
import com.jetbrains.builtInHelp.mapping.HelpMapId
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import org.jetbrains.io.send
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.xml.bind.JAXBContext

@Suppress("unused")
class HelpContentRequestHandler : HelpRequestHandlerBase() {

  private val propsToRemove = listOf(
    "searchAlgoliaApiKey", "searchAlgoliaId",
    "searchAlgoliaIndexName", "versionsService"
  )

  override fun process(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
  ): Boolean {
    for (name: String in urlDecoder.parameters().keys) {
      val param = urlDecoder.parameters()[name]
      if (param != null && (param.isEmpty() || StringUtil.isEmpty(param[0]))) {
        val mapStream = ResourceUtil.getResourceAsStream(
          HelpContentRequestHandler::class.java.classLoader,
          "topics", "Map.jhm"
        )
        val map: HelpMap = JAXBContext.newInstance(
          HelpMap::class.java, HelpMapId::class.java
        ).createUnmarshaller().unmarshal(
          mapStream
        ) as HelpMap

        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PERMANENT_REDIRECT)

        var location = "http://127.0.0.1:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}/help/${
          map.getUrlForId(
            name
          )
        }"

        if (urlDecoder.parameters().containsKey("keymap")) location += "?keymap=${URLEncoder.encode(urlDecoder.parameters()["keymap"]!![0], StandardCharsets.UTF_8)}"

        response.headers().add(
          "Location",
          location
        )

        context.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        return true
      }
    }

    when (val resourceName: String = urlDecoder.path().substringAfterLast("/")) {
      "config.json" -> {
        val configStream = ResourceUtil.getResourceAsStream(
          HelpContentRequestHandler::class.java.classLoader,
          "topics", "config.json"
        )

        @Suppress("UNCHECKED_CAST")
        val configJson: LinkedHashMap<String, Any> = jacksonObjectMapper().readValue(
          configStream,
          LinkedHashMap::class.java
        ) as LinkedHashMap<String, Any>

        configJson.keys.removeAll { propsToRemove.contains(it) }

        configJson.keys.forEach {
          if (configJson[it] is String) {
            val current: String = configJson[it] as String
            configJson[it] = current.replace(
              "https://data.services.jetbrains.com",
              "http://127.0.0.1:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}"
            ).replace(
              "true", "false"
            )
          }
        }
        sendData(
          jacksonObjectMapper().writeValueAsBytes(configJson),
          "config.json",
          request,
          context.channel(),
          request.headers()
        )
      }
      else -> {
        try {
          sendResource(
            resourceName, urlDecoder.path().substringBeforeLast("/"),
            request, context.channel(), request.headers()
          )
        }
        catch (e: Throwable) {
          HttpResponseStatus.NOT_FOUND.send(context.channel(), request, e.message)
        }
      }
    }
    return true
  }
}