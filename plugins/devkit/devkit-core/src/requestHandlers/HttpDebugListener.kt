// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.requestHandlers

import com.intellij.debugger.impl.attach.JavaAttachDebuggerProvider
import com.intellij.execution.ExecutionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.annotations.NonNls
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.send
import java.nio.charset.Charset

internal class HttpDebugListener : HttpRequestHandler() {

  companion object {
    @NonNls
    private const val PREFIX = "/debug/attachToTestProcess"
  }

  private val logger = Logger.getInstance(HttpDebugListener::class.java)

  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() == HttpMethod.POST && request.uri().startsWith(PREFIX)
  }

  override fun isAccessible(request: HttpRequest): Boolean {
    return true
  }

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val content = request.content().toString(Charset.defaultCharset())
    val contentLines = StringUtil.splitByLines(content)

    val port = contentLines[0]
    val name = contentLines.getOrNull(1)
    logger.info("Debugger attach request to a test process by port '$port' as '$name'")

    val projectHash = urlDecoder.parameters()["project-hash"]?.firstOrNull()
    val project = findTargetProject(projectHash)
    if (project == null) {
      logger.info("Suitable target project was not found")
      HttpResponseStatus.BAD_REQUEST.send(context.channel(), request)
      return true
    }
    
    ApplicationManager.getApplication().invokeAndWait {
      JavaAttachDebuggerProvider.attach("dt_socket", port, name, project) // NON-NLS
    }
    HttpResponseStatus.OK.send(context.channel(), request)
    return true
  }

  private fun findTargetProject(projectHash: String?): Project? {
    if (projectHash != null) {
      logger.debug("Locating target project by hash '$projectHash'")
      return ProjectManager.getInstance().findOpenProjectByHash(projectHash)
    }
    
    logger.debug("project-hash parameter is not specified, locating target project by active test session")
    val project = ProjectManager.getInstance().openProjects.firstOrNull { project ->
      IntelliJProjectUtil.isIntelliJPlatformProject(project) && ExecutionManager.getInstance(project).getRunningProcesses().any {
        !it.isProcessTerminated
      }
    }
    return project
  }
}