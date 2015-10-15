package org.jetbrains.rpc

interface ResultReader<RESPONSE> {
  fun <RESULT> readResult(readMethodName: String, successResponse: RESPONSE): RESULT
}