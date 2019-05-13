package org.jetbrains.protocolReader

/**
 * Signals that JSON model has some problem in it.
 */
fun JsonProtocolModelParseException(message: String): JsonProtocolModelParseException = JsonProtocolModelParseException(message, null)

class JsonProtocolModelParseException(message: String, cause: Throwable?) : RuntimeException(message, cause)
