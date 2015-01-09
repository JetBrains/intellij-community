package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

/**
 * A parser that accepts value of JSON field and outputs value in another form (e.g. string
 * is converted to enum constant) to serve field getters in JsonType interfaces.
 */
abstract class ValueReader {
  public ObjectValueReader asJsonTypeParser() {
    return null;
  }

  abstract void appendFinishedValueTypeName(@NotNull TextOutput out);

  void appendInternalValueTypeName(@NotNull FileScope scope, @NotNull TextOutput out) {
    appendFinishedValueTypeName(out);
  }

  abstract void writeReadCode(ClassScope methodScope, boolean subtyping, @NotNull TextOutput out);

  void writeArrayReadCode(@NotNull ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
    throw new UnsupportedOperationException();
  }

  protected void beginReadCall(String readPostfix, boolean subtyping, TextOutput out) {
    out.append("read");
    out.append(readPostfix).append('(');
    addReaderParameter(subtyping, out);
  }

  protected static void addReaderParameter(boolean subtyping, TextOutput out) {
    out.append(subtyping ? Util.PENDING_INPUT_READER_NAME : Util.READER_NAME);
  }

  public boolean isThrowsIOException() {
    return false;
  }
}
