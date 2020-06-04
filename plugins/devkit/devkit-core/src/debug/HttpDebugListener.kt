package org.jetbrains.idea.devkit.debug

import com.intellij.debugger.impl.attach.JavaAttachDebuggerProvider
import com.intellij.execution.ExecutionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.send
import java.nio.charset.Charset

class HttpDebugListener : HttpRequestHandler() {

  companion object {
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

    for (project in ProjectManager.getInstance().openProjects) {
      // Looking for a project with active debug session
      // TODO: Make it in a better way, check unit test configuration is running
      val executionManager = ExecutionManager.getInstance(project)
      if (executionManager.getRunningProcesses().any { !it.isProcessTerminated }) {
        ApplicationManager.getApplication().invokeAndWait {
          JavaAttachDebuggerProvider.attach("dt_socket", port, name, project)
        }
        HttpResponseStatus.OK.send(context.channel(), request)
        return true
      }
    }

    logger.info("No projects with active test session found")
    return false
  }
}