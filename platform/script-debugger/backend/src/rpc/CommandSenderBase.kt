// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.rpc

import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import org.jetbrains.jsonProtocol.Request

abstract class CommandSenderBase<SUCCESS_RESPONSE> {
  protected abstract fun <RESULT> doSend(message: Request<RESULT>, callback: RequestPromise<SUCCESS_RESPONSE, RESULT>)

  fun <RESULT : Any?> send(message: Request<RESULT>): Promise<RESULT> {
    val callback = RequestPromise<SUCCESS_RESPONSE, RESULT>(message.methodName)
    doSend(message, callback)
    return callback
  }
}

class RequestPromise<SUCCESS_RESPONSE, RESULT : Any?>(private val methodName: String?) : AsyncPromise<RESULT>(), RequestCallback<SUCCESS_RESPONSE> {
  override fun onSuccess(response: SUCCESS_RESPONSE?, resultReader: ResultReader<SUCCESS_RESPONSE>?) {
    catchError {
      val r: Any?
      if (resultReader == null || response == null) {
        r = response
      }
      else if (methodName == null) {
        r = null
      }
      else {
        r = resultReader.readResult(methodName, response)
      }

      UnsafeSetResult.setResult(this, r)
    }
  }

  override fun onError(error: Throwable) {
    setError(error)
  }
}