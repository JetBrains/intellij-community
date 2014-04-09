package org.jetbrains.protocolReader;

class ArrayReader extends ValueReader {
  private final ValueReader componentParser;
  private final boolean isList;

  ArrayReader(ValueReader componentParser, boolean isList, boolean nullable) {
    super(nullable);

    this.componentParser = componentParser;
    this.isList = isList;
  }

  @Override
  public void appendFinishedValueTypeName(TextOutput out) {
    if (isList) {
      out.append("java.util.List<");
      componentParser.appendFinishedValueTypeName(out);
      out.append('>');
    }
    else {
      componentParser.appendFinishedValueTypeName(out);
      out.append("[]");
    }
  }

  @Override
  public void appendInternalValueTypeName(FileScope scope, TextOutput out) {
    appendFinishedValueTypeName(out);
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
    componentParser.writeArrayReadCode(scope, subtyping, isNullable(), fieldName, out);
  }

  @Override
  void writeArrayReadCode(ClassScope scope,
                          boolean subtyping,
                          boolean nullable,
                          String fieldName, TextOutput out) {
  }
}
