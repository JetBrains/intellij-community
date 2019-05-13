package org.jetbrains.protocolReader

fun addReaderParameter(subtyping: Boolean, out: TextOutput) {
  if (subtyping) {
    out.append(PENDING_INPUT_READER_NAME).append("!!")
  }
  else {
    out.append(READER_NAME)
  }
}

/**
 * A parser that accepts value of JSON field and outputs value in another form (e.g. string
 * is converted to enum constant) to serve field getters in JsonType interfaces.
 */
internal abstract class ValueReader {
  open fun asJsonTypeParser(): ObjectValueReader? = null

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

  fun isThrowsIOException() = false
}
