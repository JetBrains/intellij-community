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

import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import org.jetbrains.jsonProtocol.Request

abstract class CommandSenderBase<SUCCESS_RESPONSE> {
  protected abstract fun <RESULT> doSend(message: Request<RESULT>, callback: RequestPromise<SUCCESS_RESPONSE, RESULT>)

  fun <RESULT> send(message: Request<RESULT>): Promise<RESULT> {
    val callback = RequestPromise<SUCCESS_RESPONSE, RESULT>(message.methodName)
    doSend(message, callback)
    return callback
  }
}

class RequestPromise<SUCCESS_RESPONSE, RESULT>(private val methodName: String?) : AsyncPromise<RESULT>(), RequestCallback<SUCCESS_RESPONSE> {
  override fun onSuccess(response: SUCCESS_RESPONSE?, resultReader: ResultReader<SUCCESS_RESPONSE>?) {
    catchError {
      if (resultReader == null || response == null) {
        @Suppress("UNCHECKED_CAST")
        setResult(response as RESULT?)
      }
      else {
        if (methodName == null) {
          setResult(null)
        }
        else {
          setResult(resultReader.readResult(methodName, response))
        }
      }
    }
  }

  override fun onError(error: Throwable) {
    setError(error)
  }
}