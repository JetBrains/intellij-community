// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.io.send

@Service(Service.Level.APP)
internal class HelpRequestExecutor(private val scope: CoroutineScope) {
  val availability = HelpAvailability()

  fun execute(request: FullHttpRequest, context: ChannelHandlerContext, action: suspend (FullHttpRequest) -> Unit): Job {
    val retainedRequest = request.retainedDuplicate()
    val job = scope.launch(Dispatchers.IO) {
      try {
        if (context.channel().isActive) action(retainedRequest)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.warn("Cannot serve offline help", e)
        HttpResponseStatus.INTERNAL_SERVER_ERROR.send(context.channel(), retainedRequest)
      }
    }
    job.invokeOnCompletion { retainedRequest.release() }
    return job
  }

  companion object {
    fun getInstance(): HelpRequestExecutor = ApplicationManager.getApplication().getService(HelpRequestExecutor::class.java)
  }
}
