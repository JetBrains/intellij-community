// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.testFramework.common.timeoutRunBlocking
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HelpRequestExecutorTest {
  @Test
  fun `cancelled requests release their retained buffer`(): Unit = timeoutRunBlocking {
    for (cancelBeforeStart in listOf(true, false)) {
      val owner = Job(coroutineContext.job)
      val executor = HelpRequestExecutor(CoroutineScope(coroutineContext + owner))
      val channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
      val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/help/")
      val started = CompletableDeferred<Unit>()
      try {
        if (cancelBeforeStart) owner.cancelAndJoin()
        val requestJob = executor.execute(request, channel.pipeline().lastContext()) {
          started.complete(Unit)
          awaitCancellation()
        }
        if (!cancelBeforeStart) started.await()
        owner.cancelAndJoin()
        requestJob.join()
        assertEquals(1, request.refCnt())
      }
      finally {
        owner.cancelAndJoin()
        request.release()
        channel.finishAndReleaseAll()
      }
    }
  }
}
