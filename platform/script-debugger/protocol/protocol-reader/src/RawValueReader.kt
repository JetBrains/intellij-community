package org.jetbrains.protocolReader

import org.jetbrains.io.JsonReaderEx

class RawValueReader() : ValueReader() {
  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    addReaderParameter(subtyping, out)
    out.append(".subReader();").newLine()
    addReaderParameter(subtyping, out)
    out.append(".skipValue()")
  }

  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append(javaClass<JsonReaderEx>().getCanonicalName())
  }
}
