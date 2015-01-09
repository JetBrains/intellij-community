package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class ArrayReader extends ValueReader {
  private final ValueReader componentParser;
  private final boolean isList;

  ArrayReader(@NotNull ValueReader componentParser, boolean isList) {
    super();

    this.componentParser = componentParser;
    this.isList = isList;
  }

  @Override
  public void appendFinishedValueTypeName(@NotNull TextOutput out) {
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
  void writeReadCode(ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
    componentParser.writeArrayReadCode(scope, subtyping, out);
  }
}
