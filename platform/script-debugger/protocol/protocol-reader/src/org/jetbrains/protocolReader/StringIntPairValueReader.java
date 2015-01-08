package org.jetbrains.protocolReader;

public class StringIntPairValueReader extends ValueReader {
  StringIntPairValueReader() {
    super();
  }

  @Override
  void appendFinishedValueTypeName(TextOutput out) {
    out.append("StringIntPair");
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
  }

  @Override
  void writeArrayReadCode(ClassScope scope,
                          boolean subtyping,
                          TextOutput out) {
    out.append("read").append("IntStringPairs").append('(');
    addReaderParameter(subtyping, out);
    out.append(')');
  }
}
