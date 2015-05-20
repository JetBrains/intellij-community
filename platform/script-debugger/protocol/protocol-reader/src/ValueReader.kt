package org.jetbrains.protocolReader

fun addReaderParameter(subtyping: Boolean, out: TextOutput) {
  out.append(if (subtyping) PENDING_INPUT_READER_NAME else READER_NAME)
}

/**
 * A parser that accepts value of JSON field and outputs value in another form (e.g. string
 * is converted to enum constant) to serve field getters in JsonType interfaces.
 */
abstract class ValueReader {
  open public fun asJsonTypeParser(): ObjectValueReader? {
    return null
  }

  abstract fun appendFinishedValueTypeName(out: TextOutput)

  open fun appendInternalValueTypeName(scope: FileScope, out: TextOutput) {
    appendFinishedValueTypeName(out)
  }

  abstract fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput)

  open fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    throw UnsupportedOperationException()
  }

  protected fun beginReadCall(readPostfix: String, subtyping: Boolean, out: TextOutput) {
    out.append("read")
    out.append(readPostfix).append('(')
    addReaderParameter(subtyping, out)
  }

  public fun isThrowsIOException(): Boolean {
    return false
  }
}
