package org.jetbrains.rpc

@Suppress("BASE_WITH_NULLABLE_UPPER_BOUND")
interface RequestCallback<SUCCESS_RESPONSE> {
  fun onSuccess(response: SUCCESS_RESPONSE?, resultReader: ResultReader<SUCCESS_RESPONSE>?)

  fun onError(error: Throwable)
}