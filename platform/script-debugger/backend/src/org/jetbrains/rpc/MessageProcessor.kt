package org.jetbrains.rpc

import org.jetbrains.concurrency.Promise
import org.jetbrains.jsonProtocol.Request

interface MessageProcessor {
  fun cancelWaitingRequests()

  fun closed()

  fun <RESULT : Any> send(message: Request<RESULT>): Promise<RESULT>
}