// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.robot

import com.intellij.remoterobot.client.FindByXpathRequest
import com.intellij.remoterobot.data.*
import com.intellij.remoterobot.data.js.ExecuteScriptRequest
import com.intellij.remoterobot.encryption.Encryptor
import com.intellij.remoterobot.encryption.EncryptorFactory
import com.intellij.remoterobot.fixtures.dataExtractor.server.TextToKeyCache
import com.intellij.remoterobot.services.IdeRobot
import com.intellij.remoterobot.services.LambdaLoader
import com.intellij.remoterobot.services.js.RhinoJavaScriptExecutor
import com.intellij.remoterobot.services.xpath.XpathDataModelCreator
import com.intellij.remoterobot.services.xpath.convertToHtml
import com.intellij.remoterobot.utils.serializeToBytes
import com.intellij.uiTests.robot.routing.CantFindRouteException
import com.intellij.uiTests.robot.routing.StaticFile
import com.intellij.uiTests.robot.routing.route
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.jetbrains.ide.RestService
import org.jetbrains.io.response

internal class RobotRestService : RestService() {
  private val encryptor: Encryptor by lazy { EncryptorFactory().getInstance() }

  init {
    TextToKeyCache.init(javaClass.classLoader)
  }

  private val ideRobot: IdeRobot by lazy {
    IdeRobot(TextToKeyCache, RhinoJavaScriptExecutor(), LambdaLoader())
  }

  override fun getServiceName(): String {
    return "robot"
  }

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method in listOf(HttpMethod.GET, HttpMethod.POST)
  }

  private val routing = route("/${PREFIX}/${getServiceName()}") {

    get("/hello") {
      CommonResponse(ResponseStatus.SUCCESS, "Hello", 0L)
    }

    static()

    post("/component") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          ideRobot.find(lambdaContainer = lambda)
                        }) { result ->
        FindComponentsResponse(
          elementList = listOf(result.data!!), log = result.logs, time = result.time
        )
      }
    }

    post("/{id}/component") {
      val lambda = request.receive<ObjectContainer>()
      dataResultRequest({
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.find(
                            containerId = id,
                            lambdaContainer = lambda
                          )
                        }) { result ->
        FindComponentsResponse(
          elementList = listOf(result.data!!),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/xpath/component") {
      val request = request.receive<FindByXpathRequest>()
      dataResultRequest({ ideRobot.findByXpath(request.xpath) }) { result ->
        FindComponentsResponse(
          elementList = listOf(result.data!!), log = result.logs, time = result.time
        )
      }
    }

    post("/xpath/{id}/component") {
      dataResultRequest({
                          val req = request.receive<FindByXpathRequest>()
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.findByXpath(
                            containerId = id,
                            xpath = req.xpath
                          )
                        }) { result ->
        FindComponentsResponse(
          elementList = listOf(result.data!!),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/xpath/components") {
      dataResultRequest({
                          val request = request.receive<FindByXpathRequest>()
                          ideRobot.findAllByXpath(request.xpath)
                        }) { result ->
        FindComponentsResponse(
          elementList = result.data!!, log = result.logs, time = result.time
        )
      }
    }

    post("/xpath/{id}/components") {
      dataResultRequest({
                          val req = request.receive<FindByXpathRequest>()
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.findAllByXpath(
                            containerId = id,
                            xpath = req.xpath
                          )
                        }) { result ->
        FindComponentsResponse(elementList = result.data!!, log = result.logs, time = result.time)
      }
    }

    post("/{id}/parentOfComponent") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.findParentOf(
                            containerId = id,
                            lambdaContainer = lambda
                          )
                        }) { result ->
        FindComponentsResponse(
          elementList = listOf(result.data!!),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/components") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          ideRobot.findAll(lambdaContainer = lambda)
                        }) { result ->
        FindComponentsResponse(
          elementList = result.data!!, log = result.logs, time = result.time
        )
      }
    }

    post("/{id}/components") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.findAll(
                            containerId = id,
                            lambdaContainer = lambda
                          )
                        }) { result ->
        FindComponentsResponse(elementList = result.data!!, log = result.logs, time = result.time)
      }
    }

    get("") {
      hierarchy()
    }

    get("/") {
      hierarchy()
    }

    get("/hierarchy") {
      hierarchy()
    }

    get("/highlight") {
      val parameters = QueryStringDecoder(request.uri()).parameters()
      fun intParameter(name: String): Int = parameters[name]?.firstOrNull()?.toInt() ?: throw IllegalArgumentException("$name is missed")
      val x = intParameter("x")
      val y = intParameter("y")
      val width = intParameter("width")
      val height = intParameter("height")
      ideRobot.highlight(x, y, width, height)
      "ok"
    }

    post("/execute") {
      commonRequest {
        val lambda = request.receive<ObjectContainer>()
        ideRobot.doAction(lambda)
      }
    }

    post("/js/execute") {
      commonRequest {
        val req = request.receive<ExecuteScriptRequest>()
        val decryptedRequest = req.decrypt(encryptor)
        ideRobot.doAction(decryptedRequest.script, decryptedRequest.runInEdt)
      }
    }

    post("/{id}/execute") {
      commonRequest {
        val lambda = request.receive<ObjectContainer>()
        val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
        ideRobot.doAction(id, lambda)
      }
    }

    post("/{id}/retrieveText") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.retrieveText(id, lambda)
                        }) { result ->
        CommonResponse(message = result.data!!, log = result.logs, time = result.time)
      }
    }

    post("/retrieveAny") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          ideRobot.retrieveAny(lambda)
                        }) { result ->
        ByteResponse(
          className = "",
          bytes = result.data?.serializeToBytes() ?: ByteArray(0),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/js/retrieveAny") {
      dataResultRequest({
                          val req = request.receive<ExecuteScriptRequest>()
                          val decryptedRequest = req.decrypt(encryptor)
                          ideRobot.retrieveAny(decryptedRequest.script, decryptedRequest.runInEdt)
                        }) { result ->
        println(result)
        ByteResponse(
          className = "",
          bytes = result.data?.serializeToBytes() ?: ByteArray(0),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/{id}/retrieveAny") {
      dataResultRequest({
                          val lambda = request.receive<ObjectContainer>()
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.retrieveAny(id, lambda)
                        }) { result ->
        ByteResponse(
          className = "",
          bytes = result.data?.serializeToBytes() ?: ByteArray(0),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/{id}/data") {
      dataResultRequest({
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          ideRobot.extractComponentData(id)
                        }) { result ->
        ComponentDataResponse(componentData = result.data!!, log = result.logs, time = result.time)
      }
    }

    get("/screenshot") {
      dataResultRequest({ ideRobot.makeScreenshot() }) { result ->
        ByteResponse(
          className = "",
          bytes = result.data ?: ByteArray(0),
          log = result.logs,
          time = result.time
        )
      }
    }

    get("/{componentId}/screenshot") {
      dataResultRequest({
                          val componentId =
                            pathParameters["componentId"] ?: throw IllegalArgumentException("empty componentId")
                          val isPaintingMode = urlDecoder.parameters()["isPaintingMode"]?.firstOrNull() ?: "false"
                          if (isPaintingMode.toBoolean())
                            ideRobot.makeScreenshotWithPainting(componentId)
                          else
                            ideRobot.makeScreenshot(componentId)
                        }) { result ->
        ByteResponse(
          className = "",
          bytes = result.data ?: ByteArray(0),
          log = result.logs,
          time = result.time
        )
      }
    }

    post("/{id}/js/execute") {
      commonRequest {
        val req = request.receive<ExecuteScriptRequest>()
        val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
        val decryptedRequest = req.decrypt(encryptor)

        ideRobot.doAction(id, decryptedRequest.script, decryptedRequest.runInEdt)
      }
    }


    post("/{id}/js/retrieveAny") {
      val req = request.receive<ExecuteScriptRequest>()
      dataResultRequest({
                          val id = pathParameters["id"] ?: throw IllegalArgumentException("empty id")
                          val decryptedRequest = req.decrypt(encryptor)
                          ideRobot.retrieveAny(id, decryptedRequest.script, decryptedRequest.runInEdt)
                        }) { result ->
        ByteResponse(
          className = "",
          bytes = result.data?.serializeToBytes() ?: ByteArray(0),
          log = result.logs,
          time = result.time
        )
      }
    }
  }

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    try {
      val response = when (val result = routing.handleRequest(urlDecoder, request, context)) {
        is Response -> response("application/json", Unpooled.wrappedBuffer(gson.toJson(result).toByteArray()))
        is String -> response("text/html", Unpooled.wrappedBuffer(result.toByteArray()))
        is StaticFile -> response(result.type, Unpooled.wrappedBuffer(result.byteArray))
        else -> throw NotImplementedError("${result::class.java} type is not supported")
      }
      sendResponse(request, context, response)
    }
    catch (e: CantFindRouteException) {
      e.printStackTrace()
      sendStatus(HttpResponseStatus.BAD_REQUEST, HttpUtil.isKeepAlive(request), context.channel())
    }
    return null
  }

  private inline fun <reified T> FullHttpRequest.receive(): T {
    //return mapper.readValue(content().toString(CharsetUtil.US_ASCII), T::class.java)
    return gson.fromJson(content().toString(CharsetUtil.US_ASCII), T::class.java)
  }

  private fun hierarchy(): String {
    val doc = XpathDataModelCreator(TextToKeyCache).create(null)
    return doc.convertToHtml()
      .replace("src=\"", "src=\"${getServiceName()}/")
      .replace("href=\"styles.css\"", "href=\"${getServiceName()}/styles.css\"")
  }

  private fun <T> dataResultRequest(
    code: () -> IdeRobot.Result<T>,
    responseMapper: (IdeRobot.Result<T>) -> Response
  ): Response {
    return try {
      val result = code()
      if (result.exception == null) {
        responseMapper(result)
      }
      else {
        CommonResponse(
          ResponseStatus.ERROR,
          result.exception!!.message,
          result.time,
          result.exception,
          result.logs
        )
      }
    }
    catch (e: Throwable) {
      e.printStackTrace()
      CommonResponse(ResponseStatus.ERROR, e.message ?: "", 0L, e)
    }.apply { println(this) }
  }

  private fun commonRequest(code: () -> IdeRobot.Result<Unit>): Response {
    return try {
      val result = code()
      if (result.exception == null) {
        CommonResponse(log = result.logs, time = result.time)
      }
      else {
        CommonResponse(
          ResponseStatus.ERROR,
          result.exception!!.message,
          result.time,
          result.exception,
          result.logs
        )
      }
    }
    catch (e: Throwable) {
      e.printStackTrace()
      CommonResponse(ResponseStatus.ERROR, e.message, 0L, e)
    }
  }
}