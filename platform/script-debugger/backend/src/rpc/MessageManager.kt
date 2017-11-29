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

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.jsonProtocol.Request
import java.io.IOException
import java.util.*

interface MessageProcessor {
  fun cancelWaitingRequests()

  fun closed()

  fun <RESULT> send(message: Request<RESULT>): Promise<RESULT>
}

class MessageManager<REQUEST, INCOMING, INCOMING_WITH_SEQ : Any, SUCCESS>(private val handler: MessageManager.Handler<REQUEST, INCOMING, INCOMING_WITH_SEQ, SUCCESS>) : MessageManagerBase() {
  private val callbackMap = ContainerUtil.createConcurrentIntObjectMap<RequestCallback<SUCCESS>>()

  interface Handler<OUTGOING, INCOMING, INCOMING_WITH_SEQ : Any, SUCCESS> {
    fun getUpdatedSequence(message: OUTGOING): Int

    @Throws(IOException::class)
    fun write(message: OUTGOING): Boolean

    fun readIfHasSequence(incoming: INCOMING): INCOMING_WITH_SEQ?

    fun getSequence(incomingWithSeq: INCOMING_WITH_SEQ): Int = throw AbstractMethodError()

    fun getSequence(incomingWithSeq: INCOMING_WITH_SEQ, incoming: INCOMING) = getSequence(incomingWithSeq)

    fun acceptNonSequence(incoming: INCOMING)

    fun call(response: INCOMING_WITH_SEQ, callback: RequestCallback<SUCCESS>)
  }

  fun send(message: REQUEST, callback: RequestCallback<SUCCESS>) {
    if (rejectIfClosed(callback)) {
      return
    }

    val sequence = handler.getUpdatedSequence(message)
    callbackMap.put(sequence, callback)

    val success: Boolean
    try {
      success = handler.write(message)
    }
    catch (e: Throwable) {
      try {
        failedToSend(sequence)
      }
      finally {
        LOG.error("Failed to send", e)
      }
      return
    }

    if (!success) {
      failedToSend(sequence)
    }
  }

  private fun failedToSend(sequence: Int) {
    callbackMap.remove(sequence)?.onError("Failed to send")
  }

  fun processIncoming(incomingParsed: INCOMING) {
    val commandResponse = handler.readIfHasSequence(incomingParsed)
    if (commandResponse == null) {
      if (closed) {
        // just ignore
        LOG.info("Connection closed, ignore incoming")
      }
      else {
        handler.acceptNonSequence(incomingParsed)
      }
      return
    }

    val callback = getCallbackAndRemove(handler.getSequence(commandResponse, incomingParsed))
    if (rejectIfClosed(callback)) {
      return
    }

    try {
      handler.call(commandResponse, callback)
    }
    catch (e: Throwable) {
      callback.onError(e)
      LOG.error("Failed to dispatch response to callback", e)
    }
  }

  fun getCallbackAndRemove(id: Int) = callbackMap.remove(id) ?: throw IllegalArgumentException("Cannot find callback with id $id")

  fun cancelWaitingRequests() {
    // we should call them in the order they have been submitted
    val map = callbackMap
    val keys = map.keys()
    Arrays.sort(keys)
    for (key in keys) {
      map.get(key)?.reject()
    }
  }
}