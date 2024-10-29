// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.idea.devkit.requestHandlers

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.task.ProjectTaskManager
import com.intellij.util.io.DigestUtil
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.send
import java.util.concurrent.TimeUnit

private const val PREFIX = "/devkit/make"
private val LOG = logger<CompileHttpRequestHandler>()

@Service
internal class CompileHttpRequestHandlerToken {
  // build of dev-mode make take a while, so, 15 minutes
  // (run configuration -> IDE make for configuration is started -> external process started to execute)
  private val tokens = Caffeine.newBuilder().expireAfterAccess(15, TimeUnit.MINUTES).build<String, Boolean>()

  fun acquireToken(): String {
    var token = tokens.asMap().keys.firstOrNull()
    if (token == null) {
      token = DigestUtil.randomToken()
      tokens.put(token, true)
    }
    return token
  }

  fun hasToken(token: String): Boolean = tokens.getIfPresent(token) == true
}

/**
 * Starts JPS build for targets passed in the content in JSON format (array of [BuildScopeDescription] objects).
 *
 * Currently, it's enabled for 'intellij' project only, and can be used to build additional required modules when a developer runs a test or
 * an application from the IDE.
 */
@Suppress("unused")
private class CompileHttpRequestHandler : HttpRequestHandler() {
  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() == HttpMethod.POST && request.uri().startsWith(PREFIX)
  }

  @Suppress("OPT_IN_USAGE")
  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val channel = context.channel()

    val query = urlDecoder.parameters()
    val token = query.get("token")?.firstOrNull()
    if (token == null || !service<CompileHttpRequestHandlerToken>().hasToken(token)) {
      HttpResponseStatus.FORBIDDEN.send(channel, request)
      return true
    }

    val projectHash = query.get("project-hash")?.firstOrNull()
    val project = ProjectManager.getInstance().findOpenProjectByHash(projectHash)
    if (project == null) {
      LOG.info("Project is not found (query=$query)")
      HttpResponseStatus.NOT_FOUND.send(channel, request)
      return true
    }

    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      LOG.info("Build requests are currently handled for 'intellij' project only, so request won't be processed (query=$query)")
      HttpResponseStatus.FORBIDDEN.send(channel, request)
      return true
    }

    val modules = try {
      ProtoBuf.decodeFromByteArray<List<String>>(ByteBufUtil.getBytes(request.content()))
    }
    catch (e: SerializationException) {
      LOG.info(e)
      HttpResponseStatus.BAD_REQUEST.send(channel, request)
      return true
    }

    val projectTaskManager = ProjectTaskManager.getInstance(project)
    val moduleManager = ModuleManager.getInstance(project)
    val projectTask = projectTaskManager.createModulesBuildTask(
      /* modules = */ modules.map { moduleManager.findModuleByName(it) }.toTypedArray(),
      /* isIncrementalBuild = */ true,
      /* includeDependentModules = */ false,
      /* includeRuntimeDependencies = */ false,
      /* includeTests = */ false,
    )
    projectTaskManager.run(projectTask)
      .onSuccess { taskResult ->
        val content = Unpooled.copiedBuffer("{hasErrors: ${taskResult.hasErrors()}, isAborted: ${taskResult.isAborted}}", Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        response.send(channel, request)
      }
      .onError { error ->
        HttpResponseStatus.INTERNAL_SERVER_ERROR.send(channel, request, description = "Build cancelled")
        LOG.warn(error)
      }
    return true
  }
}