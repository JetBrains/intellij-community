// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.rpc

import com.intellij.openapi.diagnostic.Logger
import io.netty.buffer.ByteBuf
import org.jetbrains.concurrency.createError
import org.jetbrains.jsonProtocol.Request
import java.util.concurrent.atomic.AtomicInteger

val LOG: Logger = Logger.getInstance(CommandProcessor::class.java)

abstract class CommandProcessor<INCOMING, INCOMING_WITH_SEQ : Any, SUCCESS_RESPONSE : Any?> : CommandSenderBase<SUCCESS_RESPONSE>(),
                                                                                              MessageManager.Handler<Request<*>, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE>,
                                                                                              ResultReader<SUCCESS_RESPONSE>,
                                                                                              MessageProcessor {
  private val currentSequence = AtomicInteger()
  @Suppress("LeakingThis")
  protected val messageManager: MessageManager<Request<*>, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE> = MessageManager(this)

  override fun cancelWaitingRequests() {
    messageManager.cancelWaitingRequests()
  }

  override fun closed() {
    messageManager.closed()
  }

  override fun getUpdatedSequence(message: Request<*>): Int {
    val id = currentSequence.incrementAndGet()
    message.finalize(id)
    return id
  }

  override final fun <RESULT> doSend(message: Request<RESULT>, callback: RequestPromise<SUCCESS_RESPONSE, RESULT>) {
    messageManager.send(message, callback)
  }
}

fun requestToByteBuf(message: Request<*>, isDebugEnabled: Boolean = LOG.isDebugEnabled): ByteBuf {
  val content = message.buffer
  if (isDebugEnabled) {
    LOG.debug("OUT: ${content.toString(Charsets.UTF_8)}")
  }
  return content
}

interface ResultReader<in RESPONSE> {
  fun <RESULT> readResult(readMethodName: String, successResponse: RESPONSE): RESULT?
}

interface RequestCallback<SUCCESS_RESPONSE> {
  fun onSuccess(response: SUCCESS_RESPONSE?, resultReader: ResultReader<SUCCESS_RESPONSE>?)

  fun onError(error: Throwable)

  fun onError(error: String): Unit = onError(createError(error))
}