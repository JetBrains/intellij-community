package org.jetbrains.rpc

import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.jsonProtocol.Request

abstract class CommandSenderBase<SUCCESS_RESPONSE> {
  protected abstract fun <RESULT : Any> doSend(message: Request<RESULT>, callback: RequestPromise<SUCCESS_RESPONSE, RESULT>)

  fun <RESULT : Any> send(message: Request<RESULT>): Promise<RESULT> {
    val callback = RequestPromise<SUCCESS_RESPONSE, RESULT>(message.methodName)
    doSend(message, callback)
    return callback
  }

  protected class RequestPromise<SUCCESS_RESPONSE, RESULT : Any>(private val methodName: String?) : AsyncPromise<RESULT>(), RequestCallback<SUCCESS_RESPONSE> {
    @Suppress("BASE_WITH_NULLABLE_UPPER_BOUND")
    override fun onSuccess(response: SUCCESS_RESPONSE?, resultReader: ResultReader<SUCCESS_RESPONSE>?) {
      try {
        if (resultReader == null || response == null) {
          @Suppress("UNCHECKED_CAST")
          setResult(response as RESULT)
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
      catch (e: Throwable) {
        LOG.error(e)
        setError(e)
      }
    }

    override fun onError(error: Throwable) {
      setError(error)
    }
  }
}