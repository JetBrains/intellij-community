package org.jetbrains.protocolReader

internal class StringIntPairValueReader : ValueReader() {
  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append("StringIntPair")
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
  }

  override fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    out.append("read").append("IntStringPairs").append('(')
    addReaderParameter(subtyping, out)
    out.append(')')
  }
}
