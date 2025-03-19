// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.idea.devkit.requestHandlers

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.compiler.impl.CompileScopeUtil
import com.intellij.compiler.impl.CompositeScope
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.project.stateStore
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.send
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import kotlin.io.path.invariantSeparatorsPathString

private const val PREFIX = "/devkit/build"
private val LOG = logger<BuildHttpRequestHandler>()

/**
 * Starts JPS build for targets passed in the content in JSON format (array of [BuildScopeDescription] objects).
 *
 * Currently, it's enabled for 'intellij' project only, and can be used to build additional required modules when a developer runs a test or
 * an application from the IDE.
 */
@Suppress("unused")
private class BuildHttpRequestHandler : HttpRequestHandler() {
  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() == HttpMethod.POST && request.uri().startsWith(PREFIX)
  }

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val query = urlDecoder.parameters()
    val projectHash = query.get("project-hash")?.firstOrNull()
    val project: Project?
    if (projectHash == null) {
      val projectPath = query.get("project-path")?.firstOrNull()
      project = ProjectManager.getInstance().openProjects.find {
        it.stateStore.projectBasePath.invariantSeparatorsPathString == projectPath
      }
    }
    else {
      project = ProjectManager.getInstance().findOpenProjectByHash(projectHash)
    }
    if (project == null) {
      LOG.info("Project is not found (query=$query)")
      HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
      return true
    }

    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) {
      LOG.info("Build requests are currently handled for 'intellij' project only, so request won't be processed (query=$query)")
      HttpResponseStatus.NOT_FOUND.send(context.channel(), request)
      return true
    }

    project.service<BuildRequestAsyncHandler>().handle(request, context.channel())
    return true
  }
}

@Service(Service.Level.PROJECT)
private class BuildRequestAsyncHandler(private val project: Project, private val coroutineScope: CoroutineScope) {
  @OptIn(ExperimentalSerializationApi::class)
  fun handle(request: FullHttpRequest, channel: Channel) {
    val scopeDescriptions = try {
      Json.decodeFromStream<List<BuildScopeDescription>>(ByteBufInputStream(request.content()))
    }
    catch (e: SerializationException) {
      LOG.info(e)
      HttpResponseStatus.BAD_REQUEST.send(channel, request)
      return
    }

    coroutineScope.launch {
      val compilerManager = project.serviceAsync<CompilerManager>()
      val scope = readAction {
        val base = CompositeScope(CompositeScope.EMPTY_ARRAY)
        CompileScopeUtil.setBaseScopeForExternalBuild(base, scopeDescriptions.map {
          TargetTypeBuildScope.newBuilder().setTypeId(it.targetType).addAllTargetId(it.targetIds).setForceBuild(it.forceBuild).build()
        })
        base
      }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        compilerManager.make(scope, CompileStatusNotification { aborted, errors, _, compileContext ->
          when {
            aborted -> HttpResponseStatus.INTERNAL_SERVER_ERROR.send(channel, request, description = "Build cancelled")
            errors == 0 -> HttpResponseStatus.OK.send(channel, request)
            else -> {
              val errorDescriptions = compileContext.getMessages(CompilerMessageCategory.ERROR).map {
                CompilationErrorDescription(
                  filePath = it.virtualFile?.path,
                  line = (it as? CompilerMessageImpl)?.line ?: 0,
                  message = it.message,
                )
              }
              val content = Unpooled.copiedBuffer(Json.encodeToString(errorDescriptions), Charsets.UTF_8)
              val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, content)
              response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
              response.send(channel, request)
            }
          }
        })
      }
    }
  }
}

@Serializable
private data class BuildScopeDescription(
  val targetType: String,
  val targetIds: List<String>,
  val forceBuild: Boolean = false,
)

@Serializable
private data class CompilationErrorDescription(
  val filePath: String?,
  val line: Int,
  val message: String,
)