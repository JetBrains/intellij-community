// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.builtInHelp.mapping.HelpMap
import com.jetbrains.builtInHelp.mapping.HelpMapId
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import org.jetbrains.io.addCommonHeaders
import org.jetbrains.io.send
import javax.xml.bind.JAXBContext

@Suppress("unused")
class HelpContentRequestHandler : HelpRequestHandlerBase() {
  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {

    if (request.uri().contains("config.json")) {

      val config = HelpRequestHandlerBase::class.java.getResource("/topics/config.json")
        .openStream().bufferedReader().use { it.readText() }.replace("https://data.services.jetbrains.com",
                                                                     "http://127.0.0.1:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}").replace(
          "true", "false")

      sendData(config.toByteArray(Charsets.UTF_8), "config.json", request, context.channel(), request.headers())
      return true
    }

    if (!urlDecoder.parameters().isEmpty()) {
      for (name: String in urlDecoder.parameters().keys) {
        val param = urlDecoder.parameters()[name]
        if (param != null && (param.isEmpty() || StringUtil.isEmpty(param[0]))) {
          val map: HelpMap? = JAXBContext.newInstance(
            HelpMap::class.java, HelpMapId::class.java).createUnmarshaller().unmarshal(
            HelpRequestHandlerBase::class.java.getResource("/topics/Map.jhm")) as HelpMap
          if (map != null) {
            val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND)
            response.addCommonHeaders()
            response.headers().add("Location", prefix + map.getUrlForId(name))
            context.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
            return true
          }
        }
      }
    }
    val resName: String = urlDecoder.path().substring(request.uri().lastIndexOf("/") + 1)

    if (StringUtil.isNotEmpty(resName)) {
      try {
        sendResource(resName, request, context.channel(), request.headers())
        return true
      }
      catch (e: Exception) {
        HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
      }
    }
    return false
  }
}