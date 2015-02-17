package org.jetbrains.protocolReader

/**
 * A parser that accepts value of JSON field and outputs value in another form (e.g. string
 * is converted to enum constant) to serve field getters in JsonType interfaces.
 */
abstract class ValueReader {
  public fun asJsonTypeParser(): ObjectValueReader? {
    return null
  }

  abstract fun appendFinishedValueTypeName(out: TextOutput)

  fun appendInternalValueTypeName(scope: FileScope, out: TextOutput) {
    appendFinishedValueTypeName(out)
  }

  abstract fun writeReadCode(methodScope: ClassScope, subtyping: Boolean, out: TextOutput)

  fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
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

  class object {

    protected fun addReaderParameter(subtyping: Boolean, out: TextOutput) {
      out.append(if (subtyping) Util.PENDING_INPUT_READER_NAME else Util.READER_NAME)
    }
  }
}
