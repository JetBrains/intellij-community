package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

public class StringIntPairValueReader extends ValueReader {
  StringIntPairValueReader() {
    super();
  }

  @Override
  void appendFinishedValueTypeName(@NotNull TextOutput out) {
    out.append("StringIntPair");
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
  }

  @Override
  void writeArrayReadCode(@NotNull ClassScope scope,
                          boolean subtyping,
                          @NotNull TextOutput out) {
    out.append("read").append("IntStringPairs").append('(');
    addReaderParameter(subtyping, out);
    out.append(')');
  }
}
