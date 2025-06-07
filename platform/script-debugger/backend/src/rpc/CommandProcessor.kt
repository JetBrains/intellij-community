// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.rpc

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.createError
import org.jetbrains.jsonProtocol.Request
import java.util.concurrent.atomic.AtomicInteger

@Deprecated("Please don't use logger from scriptDebugger", level = DeprecationLevel.HIDDEN)
val LOG: Logger = Logger.getInstance(CommandProcessor::class.java)

@ApiStatus.NonExtendable
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

  @ApiStatus.Internal
  final override fun <RESULT> doSend(message: Request<RESULT>, callback: RequestPromise<SUCCESS_RESPONSE, RESULT>) {
    messageManager.send(message, callback)
  }
}

interface ResultReader<in RESPONSE> {
  fun <RESULT> readResult(readMethodName: String, successResponse: RESPONSE): RESULT?
}

interface RequestCallback<SUCCESS_RESPONSE> {
  fun onSuccess(response: SUCCESS_RESPONSE?, resultReader: ResultReader<SUCCESS_RESPONSE>?)

  fun onError(error: Throwable)

  fun onError(error: String): Unit = onError(createError(error))
}