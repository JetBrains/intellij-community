package org.jetbrains.protocolReader

import org.jetbrains.io.JsonReaderEx

internal class RawValueReader() : ValueReader() {
  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    addReaderParameter(subtyping, out)
    out.append(".createSubReaderAndSkipValue()")
  }

  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(JsonReaderEx::class.java.canonicalName)
  }
}