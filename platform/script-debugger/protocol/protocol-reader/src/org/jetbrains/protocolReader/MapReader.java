package org.jetbrains.protocolReader;

public class MapReader extends ValueReader {
  MapReader(boolean nullable) {
    super();
  }

  @Override
  public void appendFinishedValueTypeName(TextOutput out) {
    out.append("java.util.Map");
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
    beginReadCall("Map", subtyping, out);
    out.append(')');
  }

  @Override
  void writeArrayReadCode(ClassScope scope,
                          boolean subtyping,
                          TextOutput out) {
    throw new UnsupportedOperationException();
  }
}
