package org.jetbrains.protocolReader;

import org.jetbrains.io.JsonReaderEx;

class RawValueReader extends ValueReader {
  RawValueReader(boolean nullable) {
    super();
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
    addReaderParameter(subtyping, out);
    out.append(".subReader();").newLine();
    addReaderParameter(subtyping, out);
    out.append(".skipValue()");
  }

  @Override
  public void appendFinishedValueTypeName(TextOutput out) {
    out.append(JsonReaderEx.class.getCanonicalName());
  }

  @Override
  void writeArrayReadCode(ClassScope scope,
                          boolean subtyping,
                          TextOutput out) {
    throw new UnsupportedOperationException();
  }
}
