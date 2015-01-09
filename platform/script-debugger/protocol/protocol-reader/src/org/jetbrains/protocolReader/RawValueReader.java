package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.JsonReaderEx;

class RawValueReader extends ValueReader {
  RawValueReader(boolean nullable) {
    super();
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
    addReaderParameter(subtyping, out);
    out.append(".subReader();").newLine();
    addReaderParameter(subtyping, out);
    out.append(".skipValue()");
  }

  @Override
  public void appendFinishedValueTypeName(@NotNull TextOutput out) {
    out.append(JsonReaderEx.class.getCanonicalName());
  }
}
