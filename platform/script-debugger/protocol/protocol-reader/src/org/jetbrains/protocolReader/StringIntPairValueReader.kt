package org.jetbrains.protocolReader

public class StringIntPairValueReader : ValueReader() {

  fun appendFinishedValueTypeName(out: TextOutput) {
    out.append("StringIntPair")
  }

  fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
  }

  fun writeArrayReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    out.append("read").append("IntStringPairs").append('(')
    addReaderParameter(subtyping, out)
    out.append(')')
  }
}
