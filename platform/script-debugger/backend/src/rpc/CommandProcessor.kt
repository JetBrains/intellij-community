/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.rpc

import com.intellij.openapi.diagnostic.Logger
import io.netty.buffer.ByteBuf
import org.jetbrains.concurrency.createError
import org.jetbrains.jsonProtocol.Request
import java.util.concurrent.atomic.AtomicInteger

val LOG = Logger.getInstance(CommandProcessor::class.java)

abstract class CommandProcessor<INCOMING, INCOMING_WITH_SEQ : Any, SUCCESS_RESPONSE>() : CommandSenderBase<SUCCESS_RESPONSE>(), MessageManager.Handler<Request<*>, INCOMING, INCOMING_WITH_SEQ, SUCCESS_RESPONSE>, ResultReader<SUCCESS_RESPONSE>, MessageProcessor {
  private val currentSequence = AtomicInteger()
  protected val messageManager = MessageManager(this)

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

  fun onError(error: String) = onError(createError(error))
}